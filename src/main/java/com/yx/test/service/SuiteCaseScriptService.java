package com.yx.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yx.test.entity.SuiteCaseScript;

import java.util.List;
import java.util.Map;

/**
 * 套件-用例AI转换脚本服务（套件内副本，独立于父用例脚本）
 */
public interface SuiteCaseScriptService extends IService<SuiteCaseScript> {

    /**
     * 根据套件ID和用例ID查询套件副本脚本
     */
    SuiteCaseScript getBySuiteAndCase(Long suiteId, Long caseId);

    /**
     * 异步AI转换套件副本脚本（不阻塞响应）
     *
     * @param steps        原始步骤（自然语言或Midscene格式）
     * @param targetUrl    目标URL（用于在最前面添加导航步骤）
     * @param needsClassify 是否需要AI分类（步骤含自然语言时为true）
     */
    void convertSuiteStepsAsync(Long suiteId, Long caseId,
                                List<Map<String, String>> steps,
                                String targetUrl,
                                boolean needsClassify);

    /**
     * 确保套件副本脚本存在（添加用例到套件时调用）
     * 父用例已有CONVERTED脚本时跳过（执行时回退父脚本）；否则按父用例步骤生成套件副本脚本
     */
    void ensureSuiteCaseScript(Long suiteId, Long caseId);

    /**
     * 立即写入已转换的脚本（Midscene格式直接落库，不调用AI）
     */
    void saveConvertedScript(Long suiteId, Long caseId, List<Map<String, String>> scriptSteps, String modelName);

    /**
     * 删除套件副本脚本（物理删除）
     */
    void removeSuiteCaseScript(Long suiteId, Long caseId);
}
