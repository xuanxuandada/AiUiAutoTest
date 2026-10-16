package com.yx.test.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yx.test.dto.AiRuntimeConfig;
import com.yx.test.entity.AiGenerationLog;
import com.yx.test.entity.TestCase;
import com.yx.test.mapper.AiGenerationLogMapper;
import com.yx.test.service.AiGenerationService;
import com.yx.test.service.TestCaseService;
import com.yx.test.service.UserAiConfigService;
import com.yx.test.util.AiChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * AI测试用例生成服务实现
 * 支持OpenAI兼容接口（OpenAI / DeepSeek / 通义千问 / 本地Ollama等）
 *
 * <p>关于API Key：不再从 application.yml 读取，而是按「当前登录用户」从数据库
 * （user_ai_config 表）实时解析，做到每个用户只用自己的 Key。
 * 用户没配置自己的 Key 时，会抛出业务异常提示去【设置 → AI模型配置】填写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGenerationServiceImpl implements AiGenerationService {

    private final TestCaseService testCaseService;
    private final AiGenerationLogMapper aiGenerationLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 大模型调用客户端：真正的 HTTP 请求都在这里面，本类只负责
     * 「组织提示词 + 解析返回值」，不再自己维护一份调用代码。
     */
    private final AiChatClient aiChatClient;

    /**
     * 用户AI配置服务：按登录用户解析本次要用的 AI 配置。
     * <p>注意：系统【不提供全局兜底 Key】，所以这里必须用 requireForCurrentUser()，
     * 用户没配自己的 Key 就当场报错，而不是退回去用全员的 Key。</p>
     */
    private final UserAiConfigService userAiConfigService;

    private static final String SYSTEM_PROMPT = """
            你是一个专业的软件测试工程师AI助手。你的任务是根据用户描述的功能场景，生成可以用Midscene.js执行的UI自动化测试用例。
            
            每个测试步骤(step)必须是以下格式之一：
            - {"action": "aiAction", "prompt": "自然语言描述的操作，如'点击登录按钮'"}
            - {"action": "aiInput", "prompt": "输入框描述", "value": "要输入的值"}
            - {"action": "aiAssert", "prompt": "需要断言的内容，如'页面显示成功提示'"}
            - {"action": "aiWaitFor", "prompt": "等待某个条件成立，如'加载动画消失'"}
            - {"action": "aiTap", "prompt": "点击位置的描述"}
            - {"action": "aiScroll", "prompt": "滚动方向的描述，如'向下滚动'"}
            - {"action": "navigate", "url": "目标页面URL"}  —— 当步骤包含"返回/后退/回到上一页"时，优先用navigate直接跳转到目标页面，url必须取步骤描述中明确给出的http/https地址
            - {"action": "sleep", "value": "2000"}  —— 在"返回/后退"类步骤之后必须追加此等待步骤，毫秒数固定2000，确保SPA页面渲染完成
            - {"action": "upload", "prompt": "包含本地文件路径的自然语言描述"}  —— 当步骤包含"上传附件/上传文件"操作时，把本地文件路径直接写在prompt中（格式如：点击上传按钮，选择文件C:\\Users\\admin\\Desktop\\test.jpg），执行器会自动从描述中解析出文件路径并完成上传
            
            你必须以JSON数组格式返回，每个用例包含：
            - name: 用例名称
            - origin_description: 用例描述
            - steps: 步骤数组
            - expectedResult: 预期结果
            - tags: 标签(逗号分隔)
            - priority: 优先级(1-5)
            
            只返回JSON数组，不要包含其他内容。
            """;

    private static final String CLASSIFY_SYSTEM_PROMPT = """
            你是一个专业的软件测试工程师AI助手。你的任务是将用户提供的自然语言测试步骤，精准分类并转换为Midscene.js可执行的标准格式。
            
            每个步骤必须转换为以下格式之一：
            - {"action": "aiInput", "prompt": "输入框的描述", "value": "要输入的值"}  —— 当步骤包含"输入"操作时
            - {"action": "aiTap", "prompt": "点击目标的描述"}  —— 当步骤包含"点击"操作时
            - {"action": "aiAssert", "prompt": "需要断言的内容"}  —— 当步骤包含"验证/检查/断言"操作时
            - {"action": "aiWaitFor", "prompt": "等待的条件"}  —— 当步骤仅需在当前页面等待某个条件出现（无需刷新页面）时
            - {"action": "aiWaitRefresh", "prompt": "要等待的状态描述"}  —— 当步骤包含"等待...每隔X秒刷新...直到/待...状态变为XX"时使用（如"等待，相隔5秒刷新一次，待明细列的状态全部转变为绿色"），执行器会周期性刷新页面并检查条件
            - {"action": "aiScroll", "prompt": "滚动方向的描述"}  —— 当步骤包含"滚动"操作时
            - {"action": "aiAction", "prompt": "自然语言描述的操作"}  —— 其他通用操作
            - {"action": "back"} + {"action": "sleep", "value": "2000"}  —— 当步骤包含"返回/后退/回到上一页/返回列表"且步骤描述中未明确给出目标URL时，必须使用浏览器后退动作back，严禁编造或虚构URL；仅当步骤明确给出了http/https地址时，才使用 {"action": "navigate", "url": "该地址"} + {"action": "sleep", "value": "2000"}
            - {"action": "sleep", "value": "1000"}  —— 当步骤包含"等待N秒/毫秒"时
            - {"action": "upload", "prompt": "上传按钮的描述（若原描述中包含本地文件路径，请原样保留在prompt中）"}  —— 当步骤包含"上传附件/上传文件"操作时，本地文件路径直接写在prompt中（如：点击上传按钮，选择文件C:\\Users\\admin\\Desktop\\test.jpg）
            
            关于预期结果（expectedResult）：
            - 如果用户的某个步骤隐含了预期结果（例如"点击提交，应该提示成功"），请在对应的步骤对象中添加 "expectedResult" 字段
            - 格式示例：{"action": "aiTap", "prompt": "提交按钮", "expectedResult": "页面显示提交成功提示"}
            - 没有预期结果的步骤不需要添加此字段
            - 如果用户明确标注了某步骤的预期结果，务必保留到对应步骤中
            
            你必须以JSON数组格式返回，只返回转换后的步骤数组，不要包含其他内容。
            """;

    @Override
    @Transactional
    public List<TestCase> generateTestCases(String prompt, String targetUrl) {
        log.info("AI生成测试用例，prompt: {}, targetUrl: {}", prompt, targetUrl);

        // 先按「当前登录用户」拿到本次要用的AI配置（无可用Key时直接抛业务异常）
        AiRuntimeConfig aiConfig = userAiConfigService.requireForCurrentUser();

        String userMessage = String.format("""
                请为以下功能生成测试用例：
                功能描述: %s
                目标URL: %s
                
                请生成3-5个覆盖正常流程和异常流程的测试用例。
                """, prompt, targetUrl);

        String aiResponse = aiChatClient.chat(aiConfig, SYSTEM_PROMPT, userMessage);
        List<TestCase> cases = parseAiResponse(aiResponse, targetUrl);

        // 保存到数据库
        testCaseService.saveBatch(cases);

        // 记录AI生成日志
        saveGenerationLog(aiConfig.model(), prompt, aiResponse, cases);

        log.info("AI成功生成 {} 个测试用例", cases.size());
        return cases;
    }

    @Override
    @Transactional
    public List<TestCase> generateScenario(String pageDescription, String targetUrl, int caseCount) {
        log.info("AI生成测试场景，pageDescription: {}, caseCount: {}", pageDescription, caseCount);

        // 先按「当前登录用户」拿到本次要用的AI配置（无可用Key时直接抛业务异常）
        AiRuntimeConfig aiConfig = userAiConfigService.requireForCurrentUser();

        String userMessage = String.format("""
                请为以下页面生成完整的测试场景：
                页面描述: %s
                目标URL: %s
                请生成恰好 %d 个测试用例，覆盖：
                1. 基本功能测试（正向流程）
                2. 边界条件测试
                3. 异常输入测试
                4. UI交互测试
                5. 页面元素验证
                """, pageDescription, targetUrl, caseCount);

        String aiResponse = aiChatClient.chat(aiConfig, SYSTEM_PROMPT, userMessage);
        List<TestCase> cases = parseAiResponse(aiResponse, targetUrl);

        testCaseService.saveBatch(cases);
        saveGenerationLog(aiConfig.model(), pageDescription, aiResponse, cases);

        log.info("AI成功生成场景用例 {} 个", cases.size());
        return cases;
    }

    @Override
    public List<Map<String, String>> classifySteps(List<Map<String, String>> stepsWithExpected) {
        log.info("AI分类步骤，输入步骤数: {}", stepsWithExpected.size());

        // 先按「当前登录用户」拿到本次要用的AI配置（无可用Key时直接抛业务异常）
        AiRuntimeConfig aiConfig = userAiConfigService.requireForCurrentUser();

        // 构建带预期结果的步骤文本
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stepsWithExpected.size(); i++) {
            Map<String, String> step = stepsWithExpected.get(i);
            String stepText = step.getOrDefault("step", step.getOrDefault("prompt", ""));
            String expected = step.getOrDefault("expected", "");
            sb.append("- 步骤").append(i + 1).append(": ").append(stepText);
            if (!expected.isEmpty()) {
                sb.append("  → 预期结果: ").append(expected);
            }
            sb.append("\n");
        }

        String userMessage = """
                请将以下自然语言测试步骤转换为Midscene.js可执行的标准格式：
                
                %s
                
                重要规则：
                - 如果某个步骤带有"预期结果"，请将预期结果作为 "expectedResult" 字段放到转换后对应的步骤上
                - 一个自然语言步骤可能被拆分为多个Midscene步骤，预期结果应放在执行完成后能验证该预期的那个步骤上
                - 没有预期结果的步骤不需要添加 expectedResult 字段
                
                只返回JSON数组，不要包含其他内容。
                """.formatted(sb.toString());

        String aiResponse = aiChatClient.chat(aiConfig, CLASSIFY_SYSTEM_PROMPT, userMessage);
        List<Map<String, String>> result = parseClassifiedSteps(aiResponse);

        log.info("AI分类完成，输出步骤数: {}", result.size());
        return result;
    }

    /**
     * 解析AI返回的JSON用例
     */
    private List<TestCase> parseAiResponse(String aiResponse, String targetUrl) {
        try {
            // 清理可能的代码块标记
            String jsonStr = aiResponse.trim();
            if (jsonStr.startsWith("``json")) {
                jsonStr = jsonStr.substring(7);
            } else if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();

            List<Map<String, Object>> caseList = objectMapper.readValue(jsonStr, new TypeReference<>() {});
            List<TestCase> result = new ArrayList<>();

            for (Map<String, Object> caseMap : caseList) {
                TestCase tc = new TestCase();
                tc.setName((String) caseMap.getOrDefault("name", "AI生成用例"));
                tc.setOriginDescription((String) caseMap.getOrDefault("description", ""));
                tc.setTargetUrl(targetUrl);
                tc.setExpectedResult((String) caseMap.getOrDefault("expectedResult", ""));
                tc.setType("AI_GENERATED");
                tc.setTags((String) caseMap.getOrDefault("tags", "ai-generated"));
                tc.setPriority(caseMap.containsKey("priority") ?
                        ((Number) caseMap.get("priority")).intValue() : 3);
                tc.setStatus("ACTIVE");
                tc.setCreatedBy("AI");

                // 解析步骤
                Object stepsObj = caseMap.get("steps");
                if (stepsObj instanceof List) {
                    List<Map<String, String>> steps = new ArrayList<>();
                    for (Object step : (List<?>) stepsObj) {
                        if (step instanceof Map) {
                            Map<String, String> stepMap = new HashMap<>();
                            ((Map<?, ?>) step).forEach((k, v) -> stepMap.put(String.valueOf(k), String.valueOf(v)));
                            steps.add(stepMap);
                        }
                    }
                    tc.setSteps(steps);
                } else {
                    tc.setSteps(List.of());
                }

                result.add(tc);
            }
            return result;
        } catch (Exception e) {
            log.error("解析AI返回用例失败，原始响应: {}", aiResponse, e);
            throw new RuntimeException("解析AI返回用例失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析AI返回的步骤分类结果
     */
    private List<Map<String, String>> parseClassifiedSteps(String aiResponse) {
        try {
            String jsonStr = aiResponse.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            } else if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();

            List<Map<String, Object>> stepList = objectMapper.readValue(jsonStr, new TypeReference<>() {});
            List<Map<String, String>> result = new ArrayList<>();

            for (Map<String, Object> step : stepList) {
                Map<String, String> stepMap = new HashMap<>();
                step.forEach((k, v) -> stepMap.put(String.valueOf(k), String.valueOf(v)));
                result.add(stepMap);
            }
            return result;
        } catch (Exception e) {
            log.error("解析AI步骤分类结果失败，原始响应: {}", aiResponse, e);
            throw new RuntimeException("解析AI步骤分类结果失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存AI生成日志
     *
     * @param modelName 本次实际使用的模型名（每个用户可能不同，所以必须外部传入）
     */
    private void saveGenerationLog(String modelName, String prompt, String aiResponse, List<TestCase> cases) {
        AiGenerationLog logEntity = new AiGenerationLog();
        logEntity.setPrompt(prompt);
        logEntity.setAiResponse(aiResponse);
        logEntity.setGeneratedCaseIds(cases.stream().map(TestCase::getId).toList());
        logEntity.setModelName(modelName);
        aiGenerationLogMapper.insert(logEntity);
    }
}
