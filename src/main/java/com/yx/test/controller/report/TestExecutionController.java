package com.yx.test.controller.report;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yx.test.common.Result;
import com.yx.test.entity.TestExecution;
import com.yx.test.service.DataScopeService;
import com.yx.test.service.ExecutionAiAnalysisService;
import com.yx.test.service.TestExecutionService;
import com.yx.test.util.ArtifactUrlSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 测试执行接口 (RESTful)
 */
@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class TestExecutionController {

    private final TestExecutionService testExecutionService;

    /** 数据可见范围：管「谁的执行记录/报告我能看」 */
    private final DataScopeService dataScopeService;

    /** 给报告/截图地址补上 ?token=（浏览器直接打开时带不了请求头） */
    private final ArtifactUrlSigner artifactUrlSigner;

    /** AI诊断：把失败报错交给大模型分析（用户点了才调，避免无谓消耗他自己的Key额度） */
    private final ExecutionAiAnalysisService executionAiAnalysisService;

    /**
     * 执行单个测试用例 POST /api/executions
     * Body: {"caseId": 123}
     */
    @PostMapping
    public Result<TestExecution> executeCase(@RequestBody Map<String, Long> request) {
        Long caseId = request.get("caseId");
        TestExecution execution = testExecutionService.executeCase(caseId);
        signArtifacts(List.of(execution));
        return Result.success("执行已提交", execution);
    }

    /**
     * 批量执行用例（传入用例ID列表）POST /api/executions/batch
     * Body: {"caseIds": [1, 2, 3]}
     */
    @PostMapping("/batch")
    public Result<Map<String, Object>> executeBatch(@RequestBody Map<String, List<Long>> request) {
        List<Long> caseIds = request.get("caseIds");
        Map<String, Object> result = testExecutionService.executeBatch(caseIds);
        signArtifactsIn(result);
        return Result.success(result);
    }

    /**
     * 执行测试套件 POST /api/executions/suite/{suiteId}
     * 自动执行套件中所有用例，返回批次信息用于查询结果
     */
    @PostMapping("/suite/{suiteId}")
    public Result<Map<String, Object>> executeSuite(@PathVariable Long suiteId) {
        List<TestExecution> executions = testExecutionService.executeSuite(suiteId);
        // 从第一条记录获取batchId
        String batchId = executions.isEmpty() ? null : executions.get(0).getBatchId();
        Map<String, Object> result = Map.of(
                "suiteId", suiteId,
                "batchId", batchId != null ? batchId : "",
                "totalCases", executions.size(),
                "executions", executions
        );
        signArtifactsIn(result);
        return Result.success("套件执行已提交", result);
    }

    /**
     * 查询批次列表（分页） GET /api/executions/batches?page=1&pageSize=20
     */
    @GetMapping("/batches")
    public Result<Page<Map<String, Object>>> listBatches(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(testExecutionService.listBatches(page, pageSize));
    }

    /**
     * 查询批次执行结果 GET /api/executions/batch/{batchId}
     */
    @GetMapping("/batch/{batchId}")
    public Result<Map<String, Object>> getBatchResult(@PathVariable String batchId) {
        Map<String, Object> result = testExecutionService.getBatchResult(batchId);
        signArtifactsIn(result);
        return Result.success(result);
    }

    /**
     * 分页查询执行记录 GET /api/executions?caseId={id}&batchId={x}&page=1&pageSize=20
     * caseId 和 batchId 都不传时，返回「我 + 我的下级」的执行记录
     */
    @GetMapping
    public Result<Page<TestExecution>> getHistory(
            @RequestParam(required = false) Long caseId,
            @RequestParam(required = false) String batchId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Page<TestExecution> result = testExecutionService.pageVisible(caseId, batchId, page, pageSize);
        // 只给「当前这一页」的记录拼 ?token=，不用全量处理
        signArtifacts(result.getRecords());
        return Result.success(result);
    }

    /**
     * 查询执行详情 GET /api/executions/{id}
     */
    @GetMapping("/{id}")
    public Result<TestExecution> getById(@PathVariable Long id) {
        TestExecution execution = testExecutionService.getById(id);
        if (execution == null) {
            return Result.notFound("执行记录不存在");
        }
        // 数据隔离：别人的执行记录（含报告/截图地址）不给看
        dataScopeService.assertCanAccess(execution.getUserId());
        signArtifacts(List.of(execution));
        return Result.success(execution);
    }

    /**
     * Midscene.js Runner回调接口 - 更新执行结果 POST /api/executions/callback
     */
    @PostMapping("/callback")
    public Result<Void> callback(@RequestBody Map<String, Object> request) {
        // 兼容字符串和数字类型的executionId
        Object idObj = request.get("executionId");
        Long executionId;
        if (idObj instanceof String) {
            executionId = Long.parseLong((String) idObj);
        } else {
            executionId = ((Number) idObj).longValue();
        }
        testExecutionService.updateExecutionResult(executionId, request);
        return Result.success();
    }

    /**
     * 查看测试报告 GET /api/executions/{id}/report
     * 直接重定向到本地报告文件
     */
    @GetMapping("/{id}/report")
    public Result<String> viewReport(@PathVariable Long id) {
        TestExecution execution = testExecutionService.getById(id);
        if (execution == null || execution.getReportUrl() == null) {
            return Result.notFound("报告不存在");
        }
        // 数据隔离：别人的报告不给看
        dataScopeService.assertCanAccess(execution.getUserId());
        // 直接返回报告URL，前端用 window.open 打开（前端代码不用改）
        // 地址上会自动拼好 ?token=xxx，因为浏览器直接打开链接时带不了自定义请求头
        return Result.success(artifactUrlSigner.sign(execution.getReportUrl()));
    }

    /**
     * AI诊断这条失败报错 GET /api/executions/{id}/ai-analysis
     *
     * <p>流程：读 error_message 和执行步骤 → 交给大模型 → 返回「报错含义 / 可能原因 / 解决建议」。</p>
     *
     * <p>为什么做成「用户点了才调」，而不是失败后自动分析：
     * 系统规定必须用用户自己的 API Key（没有全局兜底），自动分析会一直消耗用户自己的额度；
     * 而实际报错里相当一部分本身就是人话（例如「当前页面为登录界面，未找到某按钮」），
     * 并不需要 AI 再翻译一遍。分析结果会缓存进该记录的 result_detail.aiAnalysis，
     * 重复点击直接返回缓存（返回体里 cached=true），不再花钱。</p>
     */
    @GetMapping("/{id}/ai-analysis")
    public Result<Map<String, Object>> analyzeFailure(@PathVariable Long id) {
        return Result.success(executionAiAnalysisService.analyzeFailure(id));
    }

    // ==================== 报告/截图地址加工 ====================

    /**
     * 给一批执行记录的报告/截图地址补上 ?token=。
     * 字段名不变（还是 reportUrl / screenshotUrl），前端的打开方式（window.open）也不用改，
     * 只是地址后面多了个 token，后端才能校验「这份报告你到底能不能看」。
     *
     * 注意：只在「返回给前端的 JSON」里拼接，不能写回数据库，
     * 否则库里面存的就是带 token 的死链接，反而会泄露给别人。
     */
    private void signArtifacts(List<TestExecution> executions) {
        if (executions == null) {
            return;
        }
        for (TestExecution execution : executions) {
            if (execution == null) {
                continue;
            }
            execution.setReportUrl(artifactUrlSigner.sign(execution.getReportUrl()));
            execution.setScreenshotUrl(artifactUrlSigner.sign(execution.getScreenshotUrl()));
        }
    }

    /**
     * 批量执行 / 套件执行 / 批次结果的返回体是 Map，列表藏在 "executions" 字段里，单独处理一下。
     */
    private void signArtifactsIn(Map<String, Object> result) {
        if (result == null) {
            return;
        }
        Object executions = result.get("executions");
        if (executions instanceof List<?> list) {
            List<TestExecution> typed = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof TestExecution execution) {
                    typed.add(execution);
                }
            }
            signArtifacts(typed);
        }
    }
}
