package com.yx.test.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yx.test.dto.LoginFetchCommandRequest;
import com.yx.test.entity.RegressionSuite;

import java.util.List;

public interface RegressionSuiteService extends IService<RegressionSuite> {

    /**
     * 分页查询套件（含caseIds）
     *
     * @param keyword  按套件名称模糊匹配，或按 ID 精确匹配（不传就是查全部可见的）
     * @param id       按套件 ID 精确匹配，与 keyword 同时传时为 AND 关系
     * @param page     第几页，从 1 开始
     * @param pageSize 每页条数
     */
    Page<RegressionSuite> listWithCaseIds(String keyword, Long id, long page, long pageSize);

    /** 查询单个套件（含caseIds） */
    RegressionSuite getSuiteWithCaseIds(Long id);

    /** 保存套件并同步关联用例 */
    RegressionSuite saveSuiteWithCases(RegressionSuite suite);

    /** 更新套件并同步关联用例 */
    void updateSuiteWithCases(Long id, RegressionSuite suite);

    /** 删除套件并清理关联 */
    void deleteSuiteWithCases(Long id);

    /** 根据套件ID查询关联的用例ID列表 */
    List<Long> getCaseIdsBySuiteId(Long suiteId);

    /** 根据用例ID清理所有套件关联（用例删除时调用） */
    void removeCaseFromSuites(Long caseId);


    /** 根据套件ID保存登录态 */
    void loginFetchCommand(Long id, LoginFetchCommandRequest logintext);

    /** 查询套件下用例详情（合并套件内副本覆盖） */
    List<java.util.Map<String, Object>> getSuiteCasesDetail(Long suiteId);

    /** 更新套件内单个用例副本（保存步骤/预期结果，并触发AI转换到套件副本脚本） */
    void updateSuiteCaseCopy(Long suiteId, Long caseId,
                             java.util.List<java.util.Map<String, String>> steps,
                             String expectedResult);

    /** 从套件移除单个用例（删除关联记录+套件副本脚本） */
    void removeSuiteCase(Long suiteId, Long caseId);
}
