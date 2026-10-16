package com.yx.test.controller.testcase;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yx.test.common.Result;
import com.yx.test.dto.LoginFetchCommandRequest;
import com.yx.test.dto.SuiteCaseCopyRequest;
import com.yx.test.entity.RegressionSuite;
import com.yx.test.service.RegressionSuiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 回归测试套件管理接口 (RESTful)
 */
@RestController
@RequestMapping("/api/suites")
@RequiredArgsConstructor
public class RegressionSuiteController {

    private final RegressionSuiteService regressionSuiteService;

    /** 分页查询套件（含caseIds） GET /api/suites?keyword=&id=&page=1&pageSize=20 */
    @GetMapping
    public Result<Page<RegressionSuite>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(regressionSuiteService.listWithCaseIds(keyword, id, page, pageSize));
    }

    /** 根据ID查询套件（含caseIds） GET /api/suites/{id} */
    @GetMapping("/{id}")
    public Result<RegressionSuite> getById(@PathVariable Long id) {
        RegressionSuite suite = regressionSuiteService.getSuiteWithCaseIds(id);
        if (suite == null) {
            return Result.notFound("套件不存在");
        }
        return Result.success(suite);
    }

    /** 创建套件（含关联用例） POST /api/suites */
    @PostMapping
    public Result<RegressionSuite> create(@RequestBody RegressionSuite suite) {
        RegressionSuite saved = regressionSuiteService.saveSuiteWithCases(suite);
        saved.setCaseIds(regressionSuiteService.getCaseIdsBySuiteId(saved.getId()));
        return Result.success("套件创建成功", saved);
    }

    /** 更新套件（含关联用例） PUT /api/suites/{id} */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody RegressionSuite suite) {
        regressionSuiteService.updateSuiteWithCases(id, suite);
        return Result.success();
    }

    /** 删除套件（含清理关联） DELETE /api/suites/{id} */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        regressionSuiteService.deleteSuiteWithCases(id);
        return Result.success();
    }


    @PutMapping("/loginFetchCommand/{id}")
        public Result<Void> loginFetchCommand(@PathVariable Long id, @RequestBody LoginFetchCommandRequest loginFetchCommandRequest) {
        regressionSuiteService.loginFetchCommand(id, loginFetchCommandRequest);
        return Result.success();
    }

    /** 查询套件下用例详情（含套件内副本覆盖） GET /api/suites/{suiteId}/cases */
    @GetMapping("/{suiteId}/cases")
    public Result<List<Map<String, Object>>> getSuiteCases(@PathVariable Long suiteId) {
        return Result.success(regressionSuiteService.getSuiteCasesDetail(suiteId));
    }

    /** 更新套件内单个用例副本（保存副本步骤/预期结果，触发AI转换到套件副本脚本） PUT /api/suites/{suiteId}/cases/{caseId} */
    @PutMapping("/{suiteId}/cases/{caseId}")
    public Result<Void> updateSuiteCaseCopy(@PathVariable Long suiteId,
                                            @PathVariable Long caseId,
                                            @RequestBody SuiteCaseCopyRequest request) {
        regressionSuiteService.updateSuiteCaseCopy(suiteId, caseId, request.getSteps(), request.getExpectedResult());
        return Result.success();
    }

    /** 从套件移除单个用例（删除关联+套件副本脚本） DELETE /api/suites/{suiteId}/cases/{caseId} */
    @DeleteMapping("/{suiteId}/cases/{caseId}")
    public Result<Void> removeSuiteCase(@PathVariable Long suiteId, @PathVariable Long caseId) {
        regressionSuiteService.removeSuiteCase(suiteId, caseId);
        return Result.success();
    }
}
