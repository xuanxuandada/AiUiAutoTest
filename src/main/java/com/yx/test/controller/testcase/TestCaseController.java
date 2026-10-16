package com.yx.test.controller.testcase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yx.test.common.Result;
import com.yx.test.entity.TestCase;
import com.yx.test.entity.TestCaseScript;
import com.yx.test.entity.TestExecution;
import com.yx.test.service.AiGenerationService;
import com.yx.test.service.DataScopeService;
import com.yx.test.service.RegressionSuiteService;
import com.yx.test.service.TestCaseScriptHistoryService;
import com.yx.test.service.TestCaseScriptService;
import com.yx.test.service.TestCaseService;
import com.yx.test.service.TestExecutionService;
import com.yx.test.service.UserAiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 测试用例管理接口 (RESTful)
 */
@Slf4j
@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;
    private final AiGenerationService aiGenerationService;
    private final TestCaseScriptService testCaseScriptService;
    private final TestCaseScriptHistoryService scriptHistoryService;
    private final TestExecutionService testExecutionService;
    private final RegressionSuiteService regressionSuiteService;

    /** 用于记录脚本实际使用的模型名（按当前登录用户的配置解析） */
    private final UserAiConfigService userAiConfigService;

    /** 数据可见范围：管「谁的用例我能看/能改」 */
    private final DataScopeService dataScopeService;

    /**
     * 分页查询用例 GET /api/cases?type=&tags=&keyword=&page=1&pageSize=20
     * 只会返回「我 + 我的下级」的用例
     */
    @GetMapping
    public Result<Page<TestCase>> list(@RequestParam(required = false) String type,
                                       @RequestParam(required = false) String tags,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(testCaseService.listVisible(type, tags, keyword, page, pageSize));
    }

    /** 根据ID查询用例 GET /api/cases/{id} */
    @GetMapping("/{id}")
    public Result<TestCase> getById(@PathVariable Long id) {
        TestCase testCase = testCaseService.getById(id);
        if (testCase == null) {
            return Result.notFound("用例不存在");
        }
        // 就算猜到别人的 ID，这里也会直接 403
        dataScopeService.assertCanAccess(testCase.getUserId());
        return Result.success(testCase);
    }

    /** 创建用例 POST /api/cases */
    @PostMapping
    public Result<TestCase> create(@RequestBody TestCase testCase) {
        testCase.setType("MANUAL");

        // 防伪造归属：前端就算传了 userId 也一律抹掉，
        // 由 MyMetaObjectHandler 自动盖章为「当前登录用户」。
        testCase.setUserId(null);

        // 1. 先保存原始数据到 test_case 表
        testCaseService.save(testCase);

        // 2. 父用例保存不再自动调用AI转换：仅当步骤已是Midscene格式时直接落库脚本
        if (testCase.getSteps() != null && !testCase.getSteps().isEmpty()) {
            boolean allMidscene = testCase.getSteps().stream()
                    .allMatch(step -> step.containsKey("action"));
            TestCaseScript script = new TestCaseScript();
            script.setTestCaseId(testCase.getId());
            script.setModelName(currentModelName());
            if (allMidscene) {
                script.setScriptSteps(testCase.getSteps());
                script.setStatus("CONVERTED");
            } else {
                // 自然语言步骤：标记PENDING，由套件内编辑/保存触发AI转换
                script.setScriptSteps(List.of());
                script.setStatus("PENDING");
            }
            testCaseScriptService.save(script);
            log.info("父用例创建，caseId: {}, 脚本状态: {}", testCase.getId(), script.getStatus());
        }

        return Result.success("用例创建成功", testCase);
    }

    /** 更新用例 PUT /api/cases/{id}（父用例保存不再自动调用AI转换） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TestCase testCase) {
        // 先查旧数据：一是校验权限，二是防止改动别人的用例
        TestCase existing = testCaseService.getById(id);
        if (existing == null) {
            return Result.notFound("用例不存在");
        }
        dataScopeService.assertCanAccess(existing.getUserId());

        testCase.setId(id);
        // updateById 默认只在字段非 null 时才拼进 SQL，
        // 所以这里置 null 既不会改掉库里的归属人，也不会让前端「过户」别人的用例。
        testCase.setUserId(null);
        boolean success = testCaseService.updateById(testCase);
        if (!success) {
            return Result.error("更新失败");
        }

        // 仅同步脚本状态：Midscene格式直接落库；自然语言步骤置为PENDING（需在套件内编辑保存后由AI转换）
        if (testCase.getSteps() != null && !testCase.getSteps().isEmpty()) {
            boolean allMidscene = testCase.getSteps().stream()
                    .allMatch(step -> step.containsKey("action"));
            TestCaseScript script = testCaseScriptService.getByCaseId(id);
            if (script == null) {
                script = new TestCaseScript();
                script.setTestCaseId(id);
                script.setModelName(currentModelName());
                script.setScriptSteps(allMidscene ? testCase.getSteps() : List.of());
                script.setStatus(allMidscene ? "CONVERTED" : "PENDING");
                testCaseScriptService.save(script);
            } else {
                if (!allMidscene) {
                    // 自然语言步骤：编辑前备份旧脚本到历史表
                    scriptHistoryService.backupScript(script);
                }
                script.setScriptSteps(allMidscene ? testCase.getSteps() : List.of());
                script.setStatus(allMidscene ? "CONVERTED" : "PENDING");
                testCaseScriptService.updateById(script);
            }
            log.info("父用例更新，caseId: {}, 脚本状态: {}", id, allMidscene ? "CONVERTED" : "PENDING");
        }

        return Result.success();
    }

    /** 删除用例 DELETE /api/cases/{id}（级联删除脚本和执行记录） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        TestCase existing = testCaseService.getById(id);
        if (existing == null) {
            return Result.notFound("用例不存在");
        }
        dataScopeService.assertCanAccess(existing.getUserId());

        // 1. 逻辑删除用例本身
        boolean success = testCaseService.removeById(id);
        if (!success) {
            return Result.error("删除失败");
        }

        // 2. 级联逻辑删除关联的AI脚本
        testCaseScriptService.remove(new LambdaQueryWrapper<TestCaseScript>()
                .eq(TestCaseScript::getTestCaseId, id));

        // 3. 级联逻辑删除关联的执行记录
        testExecutionService.remove(new LambdaQueryWrapper<TestExecution>()
                .eq(TestExecution::getTestCaseId, id));

        // 4. 清理套件关联（suite_case表）
        regressionSuiteService.removeCaseFromSuites(id);

        log.info("用例及关联数据已级联删除，caseId: {}", id);
        return Result.success();
    }

    /**
     * 取「当前登录用户」实际会用的模型名，用于记录脚本元信息。
     * 用 resolveForCurrentUser() 而不是 requireForCurrentUser()：
     * 这里只是记录一个名字，用户没配 Key 也不应该报错中断创建/更新流程。
     */
    private String currentModelName() {
        return userAiConfigService.resolveForCurrentUser().model();
    }
}
