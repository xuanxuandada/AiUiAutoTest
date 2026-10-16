package com.yx.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yx.test.entity.TestCaseScript;

import java.util.List;
import java.util.Map;

public interface TestCaseScriptService extends IService<TestCaseScript> {

    /**
     * 根据用例ID查询脚本
     */
    TestCaseScript getByCaseId(Long testCaseId);

    /**
     * 异步转换测试步骤（AI处理）
     *
     * @param scriptId       脚本ID
     * @param testCaseId     用例ID
     * @param steps          原始步骤
     * @param targetUrl      目标URL（用于添加导航步骤）
     * @param needsClassify  是否需要AI分类
     */
    void convertStepsAsync(Long scriptId, Long testCaseId,
                           List<Map<String, String>> steps,
                           String targetUrl,
                           boolean needsClassify);
}
