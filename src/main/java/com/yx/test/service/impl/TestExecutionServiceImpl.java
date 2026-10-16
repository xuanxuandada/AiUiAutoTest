package com.yx.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yx.test.common.BizException;
import com.yx.test.dto.AiRuntimeConfig;
import com.yx.test.entity.RegressionSuite;
import com.yx.test.entity.SuiteCaseScript;
import com.yx.test.entity.TestCase;
import com.yx.test.entity.TestCaseScript;
import com.yx.test.entity.TestExecution;
import com.yx.test.entity.User;
import com.yx.test.mapper.TestExecutionMapper;
import com.yx.test.service.DataScopeService;
import com.yx.test.service.RegressionSuiteService;
import com.yx.test.service.SuiteCaseScriptService;
import com.yx.test.service.TestCaseScriptService;
import com.yx.test.service.TestCaseService;
import com.yx.test.service.TestExecutionService;
import com.yx.test.service.UserAiConfigService;
import com.yx.test.service.UserService;
import com.yx.test.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Base64;
import java.util.stream.Collectors;

/**
 * 测试执行服务实现
 * 负责调度Midscene.js Runner执行测试用例
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestExecutionServiceImpl extends ServiceImpl<TestExecutionMapper, TestExecution> implements TestExecutionService {

    private final TestCaseService testCaseService;
    private final TestCaseScriptService testCaseScriptService;
    private final SuiteCaseScriptService suiteCaseScriptService;
    private final RegressionSuiteService regressionSuiteService;
    private final ObjectMapper objectMapper;

    /** 按登录用户解析AI配置（Runner侧Midscene要用的地址/Key/模型） */
    private final UserAiConfigService userAiConfigService;

    /** 数据可见范围：管「谁的用例/套件/报告我能看、能执行」 */
    private final DataScopeService dataScopeService;

    /** 批次列表要把 userId 翻译成「创建人」姓名 */
    private final UserService userService;

    @Value("${midscene.runner.url:http://localhost:3001}")
    private String runnerUrl;

    @Value("${midscene.runner.timeout:120}")
    private int runnerTimeout;

    /**
     * 测试报告物理存储目录（与 Runner 的 BACKEND_REPORT_DIR 保持一致）。
     * 用于旧格式（base64 报告）兜底落盘；默认 G:/aitest/reports。
     */
    @Value("${app.artifact.report-dir:G:/aitest/reports}")
    private String reportStorageDir;

    /**
     * 截图物理存储目录（与 Runner 的 BACKEND_SCREENSHOT_DIR 保持一致）。
     * 用于旧格式（base64 截图）兜底落盘；默认 G:/aitest/screenshots。
     */
    @Value("${app.artifact.screenshot-dir:G:/aitest/screenshots}")
    private String screenshotStorageDir;

    @Override
    @Transactional
    public TestExecution executeCase(Long testCaseId) {
        // 先按「当前登录用户」解析AI配置：没有可用Key就直接抛业务异常，不产生无效执行记录
        AiRuntimeConfig aiConfig = userAiConfigService.requireForCurrentUser();

        TestCase testCase = testCaseService.getById(testCaseId);
        if (testCase == null) {
            throw new RuntimeException("测试用例不存在: " + testCaseId);
        }
        if (!"ACTIVE".equals(testCase.getStatus())) {
            throw new RuntimeException("测试用例未启用: " + testCaseId);
        }
        // 数据隔离：别人的用例不允许执行（否则跑完就能看到别人的测试步骤和报告）
        dataScopeService.assertCanAccess(testCase.getUserId());

        // 创建执行记录
        TestExecution execution = new TestExecution();
        execution.setTestCaseId(testCaseId);
        execution.setStatus("PENDING");
        execution.setExecutor("MANUAL");
        execution.setStartTime(LocalDateTime.now());
        save(execution);

        // 异步调用Midscene.js Runner（单用例执行无套件登录态）
        triggerRunner(execution.getId(), testCase, null, null, aiConfig);

        return execution;
    }

    @Override
    @Transactional
    public List<TestExecution> executeSuite(Long suiteId) {
        // 先按「当前登录用户」解析AI配置：套件内所有用例都用当前用户的Key执行
        AiRuntimeConfig aiConfig = userAiConfigService.requireForCurrentUser();

        RegressionSuite suite = regressionSuiteService.getById(suiteId);
        if (suite == null) {
            throw new RuntimeException("回归套件不存在: " + suiteId);
        }
        // 数据隔离：别人的套件不允许执行
        dataScopeService.assertCanAccess(suite.getUserId());

        // 从关联表查询用例ID
        List<Long> caseIds = regressionSuiteService.getCaseIdsBySuiteId(suiteId);
        if (caseIds == null || caseIds.isEmpty()) {
            throw new RuntimeException("回归套件中没有用例: " + suiteId);
        }

        // 启动前检查：套件下所有有效用例是否已有可执行脚本（套件副本优先，回退父用例脚本）
        checkSuiteScripts(suiteId, caseIds);

        // 生成批次ID，方便后续查询统计
        String batchId = "SUITE-" + suiteId + "-" + System.currentTimeMillis();

        // 套件级登录态命令（仅启用登录态时下发）
        String loginFetchCommand = null;
        if (suite.getLoginEnabled() != null && suite.getLoginEnabled() == 1) {
            loginFetchCommand = suite.getLoginFetchCommand();
        }

        // 更新套件最后执行时间
        suite.setLastRunTime(LocalDateTime.now());

        List<TestExecution> executions = new ArrayList<>();
        for (Long caseId : caseIds) {
            TestCase testCase = testCaseService.getById(caseId);
            if (testCase == null || !"ACTIVE".equals(testCase.getStatus())) {
                log.warn("跳过无效/未启用用例: {}", caseId);
                continue;
            }

            TestExecution execution = new TestExecution();
            execution.setTestCaseId(caseId);
            execution.setSuiteId(suiteId);
            execution.setBatchId(batchId);
            execution.setStatus("PENDING");
            execution.setExecutor("SYSTEM");
            execution.setStartTime(LocalDateTime.now());
            save(execution);

            triggerRunner(execution.getId(), testCase, suiteId, loginFetchCommand, aiConfig);
            executions.add(execution);
        }

        // 更新套件执行状态
        String resultSummary = String.format("共%d个用例已提交执行", executions.size());
        suite.setLastRunResult(resultSummary);
        regressionSuiteService.updateById(suite);

        return executions;
    }

    @Override
    public Page<TestExecution> pageVisible(Long testCaseId, String batchId, long page, long pageSize) {
        // 排除result_detail大字段，避免序列化内存溢出
        LambdaQueryWrapper<TestExecution> wrapper = new LambdaQueryWrapper<TestExecution>()
                .select(TestExecution::getId, TestExecution::getTestCaseId, TestExecution::getSuiteId,
                        TestExecution::getBatchId, TestExecution::getStatus, TestExecution::getStartTime,
                        TestExecution::getEndTime, TestExecution::getDurationMs, TestExecution::getErrorMessage,
                        TestExecution::getScreenshotUrl, TestExecution::getReportUrl, TestExecution::getExecutor,
                        TestExecution::getCreateTime, TestExecution::getUpdateTime)
                // 数据隔离：只返回「我 + 我的下级」的执行记录
                .in(TestExecution::getUserId, dataScopeService.visibleUserIds());

        // 两个筛选条件都可选：传了才拼进 SQL
        if (testCaseId != null) {
            wrapper.eq(TestExecution::getTestCaseId, testCaseId);
        }
        // batchId 以前在 controller 收了却没往下传，导致「按批次查执行记录」形同虚设，这里补上
        if (batchId != null && !batchId.isBlank()) {
            wrapper.eq(TestExecution::getBatchId, batchId);
        }

        // 排序必须留着：分页要翻页，如果没有确定的排序，
        // MySQL 每次返回的顺序可能不一样，会出现「第 1 页和第 2 页有数据重复/漏掉」。
        wrapper.orderByDesc(TestExecution::getCreateTime);

        // page(...) 是 MyBatis-Plus 提供的方法：它会先跑一句 COUNT(*)，
        // 再把原 SQL 加上 LIMIT 取当前页，返回的 Page 里就带上了 records/total/current/pages。
        return page(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public int markStaleExecutionsAsError() {
        // 直接 UPDATE，不做数据隔离。
        // 原因两点：①应用刚启动时没有登录用户（TenantContext 是空的），拿不到可见范围；
        // ②「残留任务」本身就是全库范围的概念 —— 必须把所有用户的一起清掉，
        //   否则别人的记录会永远卡在 RUNNING，他的列表页就一直转圈。
        return baseMapper.update(null, new LambdaUpdateWrapper<TestExecution>()
                .set(TestExecution::getStatus, "ERROR")
                .set(TestExecution::getErrorMessage,
                        "服务重启导致任务中断：Runner 进程随应用一起停止，不会再回调本次结果，请重新执行")
                .set(TestExecution::getEndTime, LocalDateTime.now())
                .in(TestExecution::getStatus, List.of("PENDING", "RUNNING")));
    }

    @Override
    @Transactional
    public void updateExecutionResult(Long executionId, Map<String, Object> result) {
        TestExecution execution = getById(executionId);
        if (execution == null) {
            throw new RuntimeException("执行记录不存在: " + executionId);
        }

        // Runner 的「开跑通知」（phase=start）：任务真正开始执行了，这里只推进状态。
        //
        // 为什么需要这条分支：Runner 侧加了任务队列后，一个套件的用例是排队逐个跑的，
        // 后端收到 /execute 的 200 只代表「Runner 收下了」，不代表「开始跑了」。
        // 所以 RUNNING 改由这条通知推进，界面上就不会出现「一排 RUNNING 但实际没动」。
        //
        // 关键：这里必须只改状态，绝不能碰 endTime / durationMs / resultDetail，
        // 否则会提前把任务落成「已结束」，等真正的结果回调到达时字段全部对不上。
        if ("start".equals(result.get("phase"))) {
            execution.setStatus("RUNNING");
            // 开始时间改记为「真正开跑的这一刻」：
            // 否则算出的耗时会连排队等待一起算进去（例如排到第 20 条用例
            // 明明只跑了 30 秒，却显示 15 分钟），耗时统计会失真。
            execution.setStartTime(LocalDateTime.now());
            updateById(execution);
            log.info("Runner开始执行，executionId: {}, 排队等待: {}ms", executionId, result.get("queueWaitMs"));
            return;
        }

        execution.setEndTime(LocalDateTime.now());
        if (execution.getStartTime() != null) {
            long durationMs = Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis();
            execution.setDurationMs(durationMs);
        }

        String status = (String) result.getOrDefault("status", "ERROR");
        execution.setStatus(status);
        // 拷贝并剔除超大base64字段（报告HTML/截图已落盘为本地文件，无需再存入result_detail JSON字段，
        // 否则45MB+的报告base64会超过MySQL max_allowed_packet导致回调500、任务卡死）
        Map<String, Object> detail = new LinkedHashMap<>(result);
        detail.remove("reportHtml");
        detail.remove("screenshotUrl");
        execution.setResultDetail(detail);
        execution.setErrorMessage((String) result.get("errorMessage"));
        // 处理截图：优先使用Runner直接保存的URL；旧格式base64则解码保存到本地
        String screenshotData = (String) result.get("screenshotUrl");
        if (screenshotData != null && !screenshotData.isEmpty()) {
            if (screenshotData.startsWith("/screenshots/") || screenshotData.startsWith("http")) {
                // Runner已直接保存文件到本地，直接使用URL
                execution.setScreenshotUrl(screenshotData);
            } else {
                try {
                    // 去掉 data:image/png;base64, 前缀
                    String base64Data = screenshotData;
                    if (base64Data.contains(",")) {
                        base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
                    }
                    byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                    File dir = new File(screenshotStorageDir);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    String fileName = executionId + ".png";
                    File screenshotFile = new File(dir, fileName);
                    java.nio.file.Files.write(screenshotFile.toPath(), imageBytes);
                    execution.setScreenshotUrl("/screenshots/" + fileName);
                    log.info("截图已保存到本地: {}", screenshotFile.getAbsolutePath());
                } catch (Exception e) {
                    log.error("保存截图失败: {}", e.getMessage(), e);
                    execution.setScreenshotUrl(null); // 保存失败就不存base64
                }
            }
        }

        // 处理测试报告：优先使用Runner直接复制的URL；旧格式base64则解码保存到本地
        String reportUrlIn = (String) result.get("reportUrl");
        if (reportUrlIn != null && !reportUrlIn.isEmpty()) {
            execution.setReportUrl(reportUrlIn);
        } else {
            String reportHtmlBase64 = (String) result.get("reportHtml");
            if (reportHtmlBase64 != null && !reportHtmlBase64.isEmpty()) {
                try {
                    byte[] reportBytes = Base64.getDecoder().decode(reportHtmlBase64);
                    File dir = new File(reportStorageDir);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    String fileName = executionId + ".html";
                    File reportFile = new File(dir, fileName);
                    java.nio.file.Files.write(reportFile.toPath(), reportBytes);
                    execution.setReportUrl("/reports/" + fileName);
                    log.info("测试报告已保存到本地: {}", reportFile.getAbsolutePath());
                } catch (Exception e) {
                    log.error("保存测试报告到本地失败: {}", e.getMessage(), e);
                }
            }
        }

        updateById(execution);
        log.info("用例执行完成，executionId: {}, status: {}", executionId, status);
    }

    @Override
    @Transactional
    public Map<String, Object> executeBatch(List<Long> caseIds) {
        if (caseIds == null || caseIds.isEmpty()) {
            throw new RuntimeException("用例ID列表不能为空");
        }

        // 先按「当前登录用户」解析AI配置：批次内所有用例都用当前用户的Key执行
        AiRuntimeConfig aiConfig = userAiConfigService.requireForCurrentUser();

        // 生成批次ID
        String batchId = "BATCH-" + System.currentTimeMillis();
        log.info("开始批量执行，batchId: {}, 用例数: {}", batchId, caseIds.size());

        List<TestExecution> executions = new ArrayList<>();
        int submitted = 0;

        for (Long caseId : caseIds) {
            TestCase testCase = testCaseService.getById(caseId);
            if (testCase == null || !"ACTIVE".equals(testCase.getStatus())) {
                log.warn("跳过无效/未启用用例: {}", caseId);
                continue;
            }
            // 数据隔离：批量执行里混入别人的用例也要拦掉
            dataScopeService.assertCanAccess(testCase.getUserId());

            TestExecution execution = new TestExecution();
            execution.setTestCaseId(caseId);
            execution.setBatchId(batchId);
            execution.setStatus("PENDING");
            execution.setExecutor("MANUAL");
            execution.setStartTime(LocalDateTime.now());
            save(execution);

            triggerRunner(execution.getId(), testCase, null, null, aiConfig);
            executions.add(execution);
            submitted++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("totalCases", caseIds.size());
        result.put("submitted", submitted);
        result.put("executions", executions);
        result.put("message", String.format("已提交%d个用例执行，批次ID: %s", submitted, batchId));

        return result;
    }

    @Override
    public Map<String, Object> getBatchResult(String batchId) {
        // 排除result_detail大字段，避免排序内存溢出
        List<TestExecution> executions = list(new LambdaQueryWrapper<TestExecution>()
                .select(TestExecution::getId, TestExecution::getTestCaseId, TestExecution::getSuiteId,
                        TestExecution::getBatchId, TestExecution::getStatus, TestExecution::getStartTime,
                        TestExecution::getEndTime, TestExecution::getDurationMs, TestExecution::getErrorMessage,
                        TestExecution::getScreenshotUrl, TestExecution::getReportUrl, TestExecution::getExecutor,
                        TestExecution::getCreateTime, TestExecution::getUpdateTime)
                .eq(TestExecution::getBatchId, batchId)
                // 数据隔离：别人的批次查不出来（会走到下面「批次不存在」的分支）
                .in(TestExecution::getUserId, dataScopeService.visibleUserIds())
                .orderByAsc(TestExecution::getCreateTime));

        if (executions.isEmpty()) {
            throw new RuntimeException("批次不存在或无执行记录: " + batchId);
        }

        // 统计各状态数量
        long total = executions.size();
        long passed = executions.stream().filter(e -> "PASSED".equals(e.getStatus())).count();
        long failed = executions.stream().filter(e -> "FAILED".equals(e.getStatus())).count();
        long running = executions.stream().filter(e -> "RUNNING".equals(e.getStatus())).count();
        long pending = executions.stream().filter(e -> "PENDING".equals(e.getStatus())).count();
        long error = executions.stream().filter(e -> "ERROR".equals(e.getStatus())).count();

        // 判断整体状态
        String overallStatus;
        if (pending > 0 || running > 0) {
            overallStatus = "RUNNING";
        } else if (failed > 0 || error > 0) {
            overallStatus = "FAILED";
        } else {
            overallStatus = "PASSED";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("overallStatus", overallStatus);
        result.put("total", total);
        result.put("passed", passed);
        result.put("failed", failed);
        result.put("running", running);
        result.put("pending", pending);
        result.put("error", error);
        result.put("executions", executions);

        // 用例名称：详情页要显示「用例ID + 用例名称」。
        // 执行记录里只存了 testCaseId，这里一次性查出名称给前端按 id 取，避免前端逐个请求。
        Set<Long> caseIds = executions.stream()
                .map(TestExecution::getTestCaseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> caseNames = new LinkedHashMap<>();
        if (!caseIds.isEmpty()) {
            for (TestCase testCase : testCaseService.listByIds(caseIds)) {
                if (testCase.getName() != null) {
                    // key 用字符串：雪花ID 转 JSON 后 key 本身就是字符串
                    caseNames.put(String.valueOf(testCase.getId()), testCase.getName());
                }
            }
        }
        result.put("caseNames", caseNames);

        // AI 诊断缓存：前端刷新页面后能直接回放上次的分析结果，不用再点一次（也不会重复花额度）。
        // 单独查一次（只取 id + result_detail），而不是把 resultDetail 一起塞进 executions ——
        // 那是大字段（含步骤明细），放进响应会让接口体积暴涨。
        result.put("aiAnalysis", loadCachedAiAnalyses(Collections.singletonList(batchId)));

        return result;
    }

    /**
     * 取这批执行记录里已缓存的 AI 诊断结果，形如
     * {@code { "2098596618224435201": { analysis, model, analyzedAt, ... } }}。
     *
     * <p>只 select id + result_detail，抽出 aiAnalysis 后其余大字段立即丢弃。</p>
     */
    private Map<String, Object> loadCachedAiAnalyses(Collection<String> batchIds) {
        Map<String, Object> analyses = new LinkedHashMap<>();
        if (batchIds == null || batchIds.isEmpty()) {
            return analyses;
        }
        List<TestExecution> rows = list(new LambdaQueryWrapper<TestExecution>()
                .select(TestExecution::getId, TestExecution::getResultDetail)
                .in(TestExecution::getBatchId, batchIds)
                .in(TestExecution::getUserId, dataScopeService.visibleUserIds()));

        for (TestExecution row : rows) {
            Map<String, Object> detail = row.getResultDetail();
            if (detail == null) {
                continue;
            }
            Object cached = detail.get("aiAnalysis");
            if (cached instanceof Map<?, ?> map && !map.isEmpty()) {
                // key 用字符串：雪花ID 转 JSON 后 key 本身就是字符串
                analyses.put(String.valueOf(row.getId()), cached);
            }
        }
        return analyses;
    }

    @Override
    public Page<Map<String, Object>> listBatches(long page, long pageSize) {
        // 查询所有有batchId的记录（排除result_detail大字段，避免排序内存溢出）
        // 注意：一个「批次」是对执行记录按 batchId 分组算出来的，分组前必须看到全部记录，
        // 所以这里仍然全量查一次，最后一步才手动切页（见方法末尾）。
        List<TestExecution> allExecutions = list(new LambdaQueryWrapper<TestExecution>()
                .select(TestExecution::getId, TestExecution::getTestCaseId, TestExecution::getSuiteId,
                        TestExecution::getBatchId, TestExecution::getStatus, TestExecution::getStartTime,
                        TestExecution::getEndTime, TestExecution::getDurationMs, TestExecution::getErrorMessage,
                        TestExecution::getScreenshotUrl, TestExecution::getReportUrl, TestExecution::getExecutor,
                        TestExecution::getCreateTime, TestExecution::getUpdateTime, TestExecution::getUserId)
                .isNotNull(TestExecution::getBatchId)
                // 数据隔离：批次列表里不出现别人（含别的部门）的执行记录
                .in(TestExecution::getUserId, dataScopeService.visibleUserIds())
                .orderByDesc(TestExecution::getCreateTime));

        // 创建人姓名：先把批次里出现的 userId 批量查成用户名，避免循环里 N+1 查询
        Set<Long> creatorIds = allExecutions.stream()
                .map(TestExecution::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> creatorNameMap = creatorIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(creatorIds).stream()
                        .filter(u -> u.getName() != null)
                        .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

        // 按batchId分组
        Map<String, List<TestExecution>> grouped = new LinkedHashMap<>();
        for (TestExecution exec : allExecutions) {
            grouped.computeIfAbsent(exec.getBatchId(), k -> new ArrayList<>()).add(exec);
        }

        // 为每个批次生成概要信息
        List<Map<String, Object>> batchList = new ArrayList<>();
        for (Map.Entry<String, List<TestExecution>> entry : grouped.entrySet()) {
            List<TestExecution> execs = entry.getValue();
            long total = execs.size();
            long passed = execs.stream().filter(e -> "PASSED".equals(e.getStatus())).count();
            long failed = execs.stream().filter(e -> "FAILED".equals(e.getStatus())).count();
            long running = execs.stream().filter(e -> "RUNNING".equals(e.getStatus())).count();
            long pending = execs.stream().filter(e -> "PENDING".equals(e.getStatus())).count();
            long error = execs.stream().filter(e -> "ERROR".equals(e.getStatus())).count();

            // 口径与 getBatchResult 保持一致：异常（ERROR）也算失败，否则会出现
            // 「列表页显示通过、点进详情却是失败」的前后矛盾。
            String overallStatus;
            if (pending > 0 || running > 0) {
                overallStatus = "RUNNING";
            } else if (failed > 0 || error > 0) {
                overallStatus = "FAILED";
            } else {
                overallStatus = "PASSED";
            }

            Long creatorId = execs.stream()
                    .map(TestExecution::getUserId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            Map<String, Object> batchInfo = new LinkedHashMap<>();
            batchInfo.put("batchId", entry.getKey());
            batchInfo.put("overallStatus", overallStatus);
            batchInfo.put("total", total);
            batchInfo.put("passed", passed);
            batchInfo.put("failed", failed);
            // 列表页有「运行中 / 待执行 / 异常」三列，必须和详情页下发同名字段，
            // 否则前端 BatchItem.running/pending/error 取不到值，那三列会一直空白。
            batchInfo.put("running", running);
            batchInfo.put("pending", pending);
            batchInfo.put("error", error);
            batchInfo.put("createTime", execs.get(0).getCreateTime());
            // 创建人：显示用户名，查不到时退回 userId 字符串
            batchInfo.put("creator", creatorId == null
                    ? null
                    : creatorNameMap.getOrDefault(creatorId, String.valueOf(creatorId)));
            // 持续时间(毫秒)
            batchInfo.put("durationMs", batchDurationMs(execs));
            batchList.add(batchInfo);
        }

        // 这里的列表不是「一条 SQL 查出来的」，而是上面对 allExecutions 做内存分组得到的，
        // 所以不能交给 MyBatis-Plus 的分页插件（它只会给 SQL 加 LIMIT），得自己切页：
        // 1) total 是「批次条数」，不是执行记录条数；
        // 2) 先算出当前页的起止下标（offset 可能比 total 大，比如用户在第 5 页然后切了筛选），
        //    再截取那一段，避免下标越界报 IndexOutOfBoundsException。
        long total = batchList.size();
        long offset = Math.max((page - 1) * pageSize, 0);
        int from = (int) Math.min(offset, total);
        int to = (int) Math.min(offset + pageSize, total);

        Page<Map<String, Object>> result = new Page<>(page, pageSize, total);
        // 这里复制一份再返回，不用 subList 得到的「视图」，避免持有整个 batchList 的引用
        result.setRecords(new ArrayList<>(batchList.subList(from, to)));
        return result;
    }

    /**
     * 批次持续时间(毫秒)：优先算「最早开始 → 最晚结束」的墙钟时间；
     * 批次还没跑完（拿不到全部 endTime）时，退化为各条执行耗时之和。
     */
    private long batchDurationMs(List<TestExecution> execs) {
        LocalDateTime start = execs.stream()
                .map(TestExecution::getStartTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime end = execs.stream()
                .map(TestExecution::getEndTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (start != null && end != null && !end.isBefore(start)) {
            return Duration.between(start, end).toMillis();
        }
        return execs.stream()
                .map(TestExecution::getDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }

    /**
     * 检查套件下所有有效用例是否已有可执行脚本（套件副本脚本优先，回退父用例脚本/父用例Midscene步骤）
     */
    private void checkSuiteScripts(Long suiteId, List<Long> caseIds) {
        List<String> unConvertedCases = new ArrayList<>();
        for (Long caseId : caseIds) {
            TestCase testCase = testCaseService.getById(caseId);
            if (testCase == null || !"ACTIVE".equals(testCase.getStatus())) {
                continue; // 无效/未启用的用例不参与检查
            }
            if (!hasExecutableScript(testCase, suiteId)) {
                unConvertedCases.add(testCase.getName());
            }
        }
        if (!unConvertedCases.isEmpty()) {
            throw new RuntimeException("还有测试用例未完成AI转换，请先在套件内编辑保存以触发AI转换后再执行: " + String.join("、", unConvertedCases));
        }
    }

    /**
     * 判断用例是否已有可执行脚本
     */
    private boolean hasExecutableScript(TestCase testCase, Long suiteId) {
        // 1. 套件副本脚本
        if (suiteId != null) {
            SuiteCaseScript sc = suiteCaseScriptService.getBySuiteAndCase(suiteId, testCase.getId());
            if (sc != null && "CONVERTED".equals(sc.getStatus())
                    && sc.getScriptSteps() != null && !sc.getScriptSteps().isEmpty()) {
                return true;
            }
        }
        // 2. 父用例脚本
        TestCaseScript script = testCaseScriptService.getByCaseId(testCase.getId());
        if (script != null && "CONVERTED".equals(script.getStatus())
                && script.getScriptSteps() != null && !script.getScriptSteps().isEmpty()) {
            return true;
        }
        // 3. 父用例步骤已是Midscene格式可直接执行
        return testCase.getSteps() != null && !testCase.getSteps().isEmpty()
                && testCase.getSteps().stream().allMatch(s -> s.containsKey("action"));
    }

    /**
     * 获取实际可执行的步骤
     * 优先级：套件副本脚本(suite_case_script) > 父用例脚本(test_case_script) > 父用例Midscene原始步骤
     */
    private List<Map<String, String>> resolveExecutableSteps(TestCase testCase, Long suiteId) {
        // 1. 套件副本脚本优先（套件内编辑的内容独立存储，不覆盖父用例）
        if (suiteId != null) {
            SuiteCaseScript sc = suiteCaseScriptService.getBySuiteAndCase(suiteId, testCase.getId());
            if (sc != null && "CONVERTED".equals(sc.getStatus())
                    && sc.getScriptSteps() != null && !sc.getScriptSteps().isEmpty()) {
                log.info("使用套件副本脚本，suiteId: {}, caseId: {}, 步骤数: {}", suiteId, testCase.getId(), sc.getScriptSteps().size());
                return sc.getScriptSteps();
            }
        }
        // 2. 回退父用例脚本
        TestCaseScript script = testCaseScriptService.getByCaseId(testCase.getId());
        if (script != null && "CONVERTED".equals(script.getStatus())
                && script.getScriptSteps() != null && !script.getScriptSteps().isEmpty()) {
            log.info("使用父用例脚本，caseId: {}, 步骤数: {}", testCase.getId(), script.getScriptSteps().size());
            return script.getScriptSteps();
        }
        // 3. 父用例步骤已是Midscene格式，直接使用原始步骤
        if (testCase.getSteps() != null && !testCase.getSteps().isEmpty()
                && testCase.getSteps().stream().allMatch(s -> s.containsKey("action"))) {
            log.info("直接使用父用例Midscene步骤，caseId: {}, 步骤数: {}", testCase.getId(), testCase.getSteps().size());
            return testCase.getSteps();
        }
        throw new RuntimeException("用例 [" + testCase.getName() + "] 尚未完成AI步骤转换，请先在套件内编辑保存以触发AI转换");
    }

    /**
     * 触发Midscene.js Runner执行测试
     *
     * @param loginFetchCommand 套件级登录态提取命令（浏览器Copy as fetch内容），无则传null
     * @param aiConfig          当前登录用户的AI配置（下发给Runner，保证各用户用自己的Key）
     */
    private void triggerRunner(Long executionId, TestCase testCase, Long suiteId,
                               String loginFetchCommand, AiRuntimeConfig aiConfig) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(runnerTimeout))
                    .build();

            // 解析可执行步骤：套件副本脚本优先，回退父用例脚本/原始Midscene步骤
            List<Map<String, String>> steps = resolveExecutableSteps(testCase, suiteId);

            // 构建发送给Runner的任务数据（ID用字符串，避免JS大数精度丢失）
            Map<String, Object> task = new HashMap<>();
            task.put("executionId", String.valueOf(executionId));
            task.put("testCaseId", String.valueOf(testCase.getId()));
            task.put("targetUrl", testCase.getTargetUrl());
            task.put("steps", steps);
            task.put("expectedResult", testCase.getExpectedResult());
            task.put("callbackUrl", "http://localhost:8080/api/executions/callback");
            task.put("loginFetchCommand", loginFetchCommand);

            // 把「当前登录用户自己的」AI配置一并下发给Runner。
            // Runner会在创建Midscene Agent时用它做隔离，避免多用户并发时串用Key。
            Long currentUserId = TenantContext.getCurrentUserId();
            task.put("userId", currentUserId == null ? null : String.valueOf(currentUserId));
            task.put("aiApiKey", aiConfig.apiKey());
            task.put("aiModel", aiConfig.model());
            task.put("aiModelFamily", aiConfig.modelFamily());
            task.put("aiBaseUrl", aiConfig.baseUrl());   // 必须是「基址」，不含 /chat/completions

            String jsonBody = objectMapper.writeValueAsString(task);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(runnerUrl + "/execute"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(runnerTimeout))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            // 异步发送，不阻塞主流程
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            // Runner 返回 200 只代表「任务已收下」。Runner 侧加了任务队列后，
                            // 收下 ≠ 开始执行（可能还在排队），所以这里不再置 RUNNING，
                            // 否则界面上会出现「一排 RUNNING 但实际没动」。
                            // 状态改为由 Runner 真正开跑时的开跑通知（callback 里 phase=start）推进。
                            log.info("Runner已接收任务（等待开跑通知），executionId: {}", executionId);
                        } else {
                            log.error("Runner接受任务失败，executionId: {}, status: {}", executionId, response.statusCode());
                            // 原来这里只打了日志、没有改状态，记录会永久停在 PENDING。
                            // Runner 明确拒收（HTTP 非 200）意味着这个任务根本不会被调度执行，
                            // 属于「已经确定失败」，必须立刻落成终态，否则前端会一直转圈。
                            TestExecution exec = getById(executionId);
                            if (exec != null) {
                                exec.setStatus("ERROR");
                                exec.setErrorMessage("Runner 拒绝任务，HTTP " + response.statusCode());
                                exec.setEndTime(LocalDateTime.now());
                                updateById(exec);
                            }
                        }
                    })
                    .exceptionally(e -> {
                        log.error("调用Runner异常，executionId: {}", executionId, e);
                        TestExecution exec = getById(executionId);
                        if (exec != null) {
                            exec.setStatus("ERROR");
                            exec.setErrorMessage("调用Runner异常: " + e.getMessage());
                            exec.setEndTime(LocalDateTime.now());
                            updateById(exec);
                        }
                        return null;
                    });

        } catch (Exception e) {
            log.error("触发Runner失败", e);
            TestExecution exec = getById(executionId);
            if (exec != null) {
                exec.setStatus("ERROR");
                exec.setErrorMessage("触发Runner失败: " + e.getMessage());
                // 落终态时必须同时写上结束时间，否则这条记录「状态是ERROR但一直没结束」，
                // 批次耗时等统计会算不出来
                exec.setEndTime(LocalDateTime.now());
                updateById(exec);
            }
        }
    }
}
