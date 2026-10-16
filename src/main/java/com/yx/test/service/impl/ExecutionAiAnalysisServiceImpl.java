package com.yx.test.service.impl;

import com.yx.test.common.BizException;
import com.yx.test.dto.AiRuntimeConfig;
import com.yx.test.entity.TestCase;
import com.yx.test.entity.TestExecution;
import com.yx.test.service.DataScopeService;
import com.yx.test.service.ExecutionAiAnalysisService;
import com.yx.test.service.TestCaseService;
import com.yx.test.service.TestExecutionService;
import com.yx.test.service.UserAiConfigService;
import com.yx.test.util.AiChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行失败「AI 诊断」服务实现。
 *
 * <p><b>数据流向：</b>
 * 前端点击「AI 诊断」→ 本类读 test_execution 里的 error_message 和 result_detail.stepResults
 * → 组提示词交给 {@link AiChatClient} → 把模型回复写回 result_detail.aiAnalysis（缓存）
 * → 返回给前端。</p>
 *
 * <p><b>为什么用 result_detail 缓存，而不新增一个数据库列？</b>
 * ① 不用改表结构（不用动 init.sql，也不用给已有数据补字段）；
 * ② result_detail 是 JSON 字段，本来就用来装「执行结果的杂项」，AI 诊断天然属于这里；
 * ③ 列表接口 /api/executions 用 .select(...) 排除了 result_detail，
 * 所以这段几百字的诊断文本不会把列表接口的响应撑大——只有详情接口
 * /api/executions/{id} 和本接口会返回它，正好符合「按需查看」的定位。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionAiAnalysisServiceImpl implements ExecutionAiAnalysisService {

    private final TestExecutionService testExecutionService;
    private final TestCaseService testCaseService;

    /** 按当前登录用户解析 AI 配置：无可用 Key 时会直接抛 BizException 提示去配置 */
    private final UserAiConfigService userAiConfigService;

    /** 数据可见范围：管「谁的执行记录我能看」 */
    private final DataScopeService dataScopeService;

    /** 真正发 HTTP 请求调模型的地方 */
    private final AiChatClient aiChatClient;

    /** 缓存在 result_detail 里的键名 */
    private static final String DETAIL_KEY_AI_ANALYSIS = "aiAnalysis";

    /** 步骤上下文最多放多少字符（避免把提示词撑得过大、白花 token） */
    private static final int MAX_CONTEXT_CHARS = 3000;

    /**
     * 诊断用的系统提示词。
     * 之所以把输出格式写死成三段：前端可以直接把这段文本整块渲染出来，
     * 不需要再去解析 JSON；同时固定结构能让模型的回答稳定、不跑偏。
     */
    private static final String SYSTEM_PROMPT = """
            你是一个资深的 UI 自动化测试排障专家。用户会给你一条自动化测试用例执行失败时的报错，以及相关上下文。

            请用中文输出诊断结论，必须固定分为以下三段，不要输出任何额外内容：

            【报错含义】用一两句大白话说明这条报错到底在说什么，把英文术语和内部堆栈翻译成人话。
            【可能原因】按可能性从高到低列出 1-3 条原因。
            【解决建议】针对每条原因给出具体、可操作的动作（例如去哪个页面的哪项设置、检查什么内容）。

            要求：
            - 只依据用户提供的信息判断。信息不足时直接说明还缺什么信息，不要编造原因。
            - 如果报错原文里已经给出了建议（例如提示调大某个参数、提示去查某个文档），必须在【解决建议】里明确指出。
            - 全文控制在 300 字以内，不要写客套话。
            """;

    @Override
    public Map<String, Object> analyzeFailure(Long executionId) {
        TestExecution execution = testExecutionService.getById(executionId);
        if (execution == null) {
            throw BizException.notFound("执行记录不存在: " + executionId);
        }

        // 数据隔离：别人的执行记录（连带报错内容、截图地址）不给看
        dataScopeService.assertCanAccess(execution.getUserId());

        String status = execution.getStatus();
        // 只有失败/异常才需要诊断。PASSED 的用例问 AI「这是什么报错」没有意义；
        // PENDING/RUNNING 还没跑完，也不会消耗 AI 额度去猜。
        // 这里用「用户能看懂的话」提示，而不是抛 RuntimeException——
        // 后者会被 GlobalExceptionHandler 改写成「系统异常，请联系管理员」。
        if (!"FAILED".equals(status) && !"ERROR".equals(status)) {
            throw BizException.badRequest("该执行记录当前状态是 " + status + "，只有失败（FAILED）或异常（ERROR）的记录才需要AI诊断");
        }

        String errorMessage = execution.getErrorMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
            // 典型场景：应用重启时被 markStaleExecutionsAsError() 置为 ERROR 的残留记录，
            // 它们根本没有报错内容，硬问 AI 只会浪费额度。
            throw BizException.badRequest("该执行记录没有失败信息（errorMessage 为空），无法进行AI诊断");
        }

        // 先看有没有缓存：用户可能对同一条失败记录反复点击「AI 诊断」，
        // 第二次开始直接回放上次结果，不再花用户的 token。
        Map<String, Object> cached = readCachedAnalysis(execution);
        if (cached != null) {
            Map<String, Object> hit = new LinkedHashMap<>(cached);
            hit.put("cached", true);
            hit.put("executionId", executionId);
            log.info("命中已缓存的AI诊断，executionId: {}, 原分析时间: {}", executionId, cached.get("analyzedAt"));
            return hit;
        }

        // 走到这里才真正调模型：Key 从「当前登录用户」解析，没配就当场报错提示去配置
        AiRuntimeConfig aiConfig = userAiConfigService.requireForCurrentUser();

        String userMessage = buildUserMessage(execution, errorMessage);
        long startMs = System.currentTimeMillis();
        String analysis = aiChatClient.chat(aiConfig, SYSTEM_PROMPT, userMessage);
        log.info("AI诊断完成，executionId: {}, model: {}, 耗时: {}ms",
                executionId, aiConfig.model(), System.currentTimeMillis() - startMs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionId", executionId);
        result.put("cached", false);
        result.put("model", aiConfig.model());
        result.put("analyzedAt", LocalDateTime.now().toString());
        result.put("errorMessage", errorMessage);
        result.put("analysis", analysis);

        saveAnalysis(execution, result);
        return result;
    }

    /**
     * 从 result_detail 里读上一次诊断结果。
     *
     * @return 有缓存返回该 Map，没有返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readCachedAnalysis(TestExecution execution) {
        Map<String, Object> detail = execution.getResultDetail();
        if (detail == null) {
            return null;
        }
        Object cached = detail.get(DETAIL_KEY_AI_ANALYSIS);
        if (cached instanceof Map<?, ?> map && !map.isEmpty()) {
            // JSON 字段反序列化回来就是 LinkedHashMap，这里做一次强转
            return (Map<String, Object>) map;
        }
        return null;
    }

    /**
     * 把诊断结果合并写回 result_detail。
     *
     * <p>注意是「合并」而不是「覆盖」：result_detail 里还存着 stepResults、
     * expectedResult 等执行详情，直接整体替换会把它们弄丢。</p>
     *
     * <p>另外这里没有加 @Transactional：本方法内不涉及多表写操作，
     * 只有一次 update。而外层真正的耗时操作（调 AI）发生在 update 之前，
     * 如果给整个方法加事务，等于让数据库连接在 AI 思考的那几十秒里一直被占着，
     * 并发一高就会把连接池耗光。</p>
     */
    private void saveAnalysis(TestExecution execution, Map<String, Object> analysis) {
        Map<String, Object> existing = execution.getResultDetail();
        Map<String, Object> detail = existing == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(existing);
        detail.put(DETAIL_KEY_AI_ANALYSIS, analysis);
        execution.setResultDetail(detail);
        testExecutionService.updateById(execution);
    }

    /**
     * 组装发给模型的用户消息：用例信息 + 失败报错 + 执行步骤记录。
     * 给的信息越贴近现场，模型的判断越准。
     */
    private String buildUserMessage(TestExecution execution, String errorMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("【用例信息】\n");

        // 用例名和目标URL只是「锦上添花」的上下文：查不到就不写，
        // 绝不能因为这里出问题导致整个诊断不可用。
        try {
            TestCase testCase = testCaseService.getById(execution.getTestCaseId());
            if (testCase != null) {
                sb.append("用例名称: ").append(testCase.getName()).append("\n");
                sb.append("目标URL: ").append(testCase.getTargetUrl()).append("\n");
            }
        } catch (Exception e) {
            log.warn("查询用例信息失败，跳过该部分上下文: {}", e.getMessage());
        }

        sb.append("\n【失败报错】\n").append(errorMessage).append("\n");

        String stepsContext = buildStepsContext(execution);
        if (!stepsContext.isBlank()) {
            sb.append("\n【执行步骤记录】\n").append(stepsContext);
        }

        sb.append("\n请分析这条报错。");
        return sb.toString();
    }

    /**
     * 把 result_detail.stepResults 整理成一行一步的纯文本。
     *
     * <p>为什么要喂步骤：光看「步骤 2 执行失败」模型没法判断是哪一步、
     * 前面做了什么。补上「失败那一步的描述 + 它的错误信息」，
     * 模型才能给出像样的原因分析。</p>
     */
    private String buildStepsContext(TestExecution execution) {
        Map<String, Object> detail = execution.getResultDetail();
        if (detail == null) {
            return "";
        }
        Object stepsObj = detail.get("stepResults");
        if (!(stepsObj instanceof List<?> steps) || steps.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Object item : steps) {
            if (!(item instanceof Map<?, ?> stepResult)) {
                continue;
            }
            Map<?, ?> stepMap = (stepResult.get("step") instanceof Map<?, ?> m) ? m : Map.of();
            // 这里不能用 getOrDefault("action", "")：stepMap 的键值类型是通配符，
            // 值类型会被编译器推断成 capture<?>，而 getOrDefault 的默认值参数必须是该 capture 类型，
            // 传 String 字面量会直接编译报错。所以先 get 出来再自己判空。
            Object actionObj = stepMap.get("action");
            String action = actionObj == null ? "" : String.valueOf(actionObj);
            Object promptObj = stepMap.get("prompt");
            String prompt = promptObj == null ? "" : String.valueOf(promptObj);

            sb.append(index++).append(". [").append(action).append("] ").append(prompt).append(" → ");

            if ("FAILED".equals(stepResult.get("assertResult"))) {
                sb.append("断言失败: ").append(stepResult.get("assertError"));
            } else if (Boolean.FALSE.equals(stepResult.get("success"))) {
                sb.append("执行失败: ").append(stepResult.get("error"));
            } else {
                sb.append("成功");
            }
            sb.append("\n");

            // 步骤特别多（例如循环刷新的用例）时截断，避免提示词过大白花 token
            if (sb.length() > MAX_CONTEXT_CHARS) {
                sb.append("...(步骤较多，已省略剩余部分)\n");
                break;
            }
        }
        return sb.toString();
    }
}
