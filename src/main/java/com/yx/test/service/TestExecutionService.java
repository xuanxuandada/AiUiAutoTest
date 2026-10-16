package com.yx.test.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yx.test.entity.TestExecution;

import java.util.List;
import java.util.Map;

public interface TestExecutionService extends IService<TestExecution> {

    /**
     * 执行单个测试用例
     *
     * @param testCaseId 用例ID
     * @return 执行记录
     */
    TestExecution executeCase(Long testCaseId);

    /**
     * 批量执行测试用例（回归测试）
     *
     * @param suiteId 回归套件ID
     * @return 执行记录列表
     */
    List<TestExecution> executeSuite(Long suiteId);

    /**
     * 分页查询「我可见的」执行记录（我 + 我的下级创建的）。
     * caseId / batchId 都可以不传（不传就是不加该条件）。
     *
     * @param testCaseId 用例ID，可空
     * @param batchId    批次ID，可空
     * @param page       第几页，从 1 开始
     * @param pageSize   每页条数
     */
    Page<TestExecution> pageVisible(Long testCaseId, String batchId, long page, long pageSize);

    /**
     * 应用启动时，把上次运行残留的 PENDING/RUNNING 记录一次性置为 ERROR。
     *
     * <p>为什么一定会有残留：Midscene Runner 是随本进程一起启动的 Node 子进程，
     * 应用关闭时会被一起杀掉，Runner 内存里排队/执行中的任务随之消失，
     * 数据库里这些记录就永远等不到 /api/executions/callback 了。
     *
     * @return 被修正的记录条数
     */
    int markStaleExecutionsAsError();

    /**
     * 回调更新执行结果（由Midscene.js Runner调用）
     *
     * @param executionId 执行ID
     * @param result      结果数据
     */
    void updateExecutionResult(Long executionId, Map<String, Object> result);

    /**
     * 批量执行：传入用例ID列表，直接后台跑Midscene.js
     *
     * @param caseIds 用例ID列表
     * @return 批次执行结果（含batchId用于后续查询）
     */
    Map<String, Object> executeBatch(List<Long> caseIds);

    /**
     * 查询批次执行结果
     *
     * @param batchId 批次ID
     * @return 批次概要 + 每个用例的执行详情
     */
    Map<String, Object> getBatchResult(String batchId);

    /**
     * 分页查询批次列表（按创建时间倒序）
     *
     * @param page     第几页，从 1 开始
     * @param pageSize 每页条数
     * @return 批次概要分页结果，每个批次包含 batchId、状态统计等
     */
    Page<Map<String, Object>> listBatches(long page, long pageSize);
}
