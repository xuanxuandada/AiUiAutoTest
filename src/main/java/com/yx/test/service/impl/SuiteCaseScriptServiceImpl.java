package com.yx.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yx.test.entity.SuiteCaseScript;
import com.yx.test.entity.TestCase;
import com.yx.test.entity.TestCaseScript;
import com.yx.test.mapper.SuiteCaseScriptMapper;
import com.yx.test.service.AiGenerationService;
import com.yx.test.service.SuiteCaseScriptService;
import com.yx.test.service.TestCaseScriptService;
import com.yx.test.service.TestCaseService;
import com.yx.test.service.UserAiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 套件-用例AI转换脚本服务实现
 * 转换结果写入 suite_case_script 表，与父用例 test_case_script 完全隔离
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuiteCaseScriptServiceImpl extends ServiceImpl<SuiteCaseScriptMapper, SuiteCaseScript>
        implements SuiteCaseScriptService {

    private final AiGenerationService aiGenerationService;
    private final TestCaseService testCaseService;
    private final TestCaseScriptService testCaseScriptService;

    /** 用于记录脚本实际使用的模型名（按当前登录用户的配置解析） */
    private final UserAiConfigService userAiConfigService;

    @Override
    public SuiteCaseScript getBySuiteAndCase(Long suiteId, Long caseId) {
        return getOne(new LambdaQueryWrapper<SuiteCaseScript>()
                .eq(SuiteCaseScript::getSuiteId, suiteId)
                .eq(SuiteCaseScript::getCaseId, caseId));
    }

    @Override
    public void ensureSuiteCaseScript(Long suiteId, Long caseId) {
        // 已有套件副本脚本记录则不动（避免覆盖用户在套件内编辑的内容）
        if (getBySuiteAndCase(suiteId, caseId) != null) {
            return;
        }
        // 父用例脚本已CONVERTED则无需副本（执行时回退父脚本）
        TestCaseScript parentScript = testCaseScriptService.getByCaseId(caseId);
        if (parentScript != null && "CONVERTED".equals(parentScript.getStatus())
                && parentScript.getScriptSteps() != null && !parentScript.getScriptSteps().isEmpty()) {
            return;
        }
        TestCase testCase = testCaseService.getById(caseId);
        if (testCase == null || testCase.getSteps() == null || testCase.getSteps().isEmpty()) {
            return;
        }
        boolean needsClassify = testCase.getSteps().stream().anyMatch(step -> !step.containsKey("action"));
        SuiteCaseScript script = new SuiteCaseScript();
        script.setSuiteId(suiteId);
        script.setCaseId(caseId);
        script.setModelName(currentModelName());
        if (!needsClassify) {
            // 已是Midscene格式，无需AI，直接落库
            script.setScriptSteps(testCase.getSteps());
            script.setStatus("CONVERTED");
            save(script);
            log.info("套件副本脚本直接落库（无需AI），suiteId: {}, caseId: {}", suiteId, caseId);
        } else {
            // 自然语言步骤：创建PENDING后异步AI转换
            script.setScriptSteps(List.of());
            script.setStatus("PENDING");
            save(script);
            convertSuiteStepsAsync(suiteId, caseId, testCase.getSteps(), testCase.getTargetUrl(), true);
        }
    }

    @Async
    @Override
    public void convertSuiteStepsAsync(Long suiteId, Long caseId,
                                       List<Map<String, String>> steps,
                                       String targetUrl,
                                       boolean needsClassify) {
        log.info("开始异步转换套件副本脚本，suiteId: {}, caseId: {}, needsClassify: {}", suiteId, caseId, needsClassify);

        SuiteCaseScript script = getBySuiteAndCase(suiteId, caseId);
        if (script == null) {
            script = new SuiteCaseScript();
            script.setSuiteId(suiteId);
            script.setCaseId(caseId);
            script.setModelName(currentModelName());
        }

        try {
            List<Map<String, String>> finalSteps;
            if (needsClassify) {
                // 提取自然语言步骤和预期结果，一起传给AI
                List<Map<String, String>> stepsForAi = steps.stream()
                        .map(step -> {
                            Map<String, String> map = new HashMap<>();
                            String stepText = step.getOrDefault("prompt", "");
                            if (stepText.isEmpty()) {
                                stepText = step.getOrDefault("step", "");
                            }
                            map.put("step", stepText);
                            map.put("expected", step.getOrDefault("expected", ""));
                            return map;
                        })
                        .toList();
                log.info("套件副本提取的步骤及预期: {}", stepsForAi);

                List<Map<String, String>> classifiedSteps = aiGenerationService.classifySteps(stepsForAi);
                log.info("套件副本AI转换后的步骤: {}", classifiedSteps);
                finalSteps = new ArrayList<>(classifiedSteps);
            } else {
                finalSteps = new ArrayList<>(steps);
            }

            // 如果有targetUrl，在最前面加导航步骤
            if (targetUrl != null && !targetUrl.isEmpty()) {
                Map<String, String> navigateStep = new HashMap<>();
                navigateStep.put("action", "navigate");
                navigateStep.put("url", targetUrl);
                finalSteps.add(0, navigateStep);
            }

            script.setScriptSteps(finalSteps);
            script.setStatus("CONVERTED");
        } catch (Exception e) {
            log.error("套件副本AI步骤分类失败，suiteId: {}, caseId: {}", suiteId, caseId, e);
            script.setScriptSteps(List.of());
            script.setStatus("FAILED");
        }

        if (script.getId() == null) {
            save(script);
        } else {
            updateById(script);
        }
        log.info("套件副本脚本转换完成，suiteId: {}, caseId: {}, status: {}", suiteId, caseId, script.getStatus());
    }

    @Override
    public void saveConvertedScript(Long suiteId, Long caseId, List<Map<String, String>> scriptSteps, String modelName) {
        SuiteCaseScript script = getBySuiteAndCase(suiteId, caseId);
        if (script == null) {
            script = new SuiteCaseScript();
            script.setSuiteId(suiteId);
            script.setCaseId(caseId);
            script.setModelName(modelName);
        }
        script.setScriptSteps(scriptSteps);
        script.setStatus("CONVERTED");
        if (script.getId() == null) {
            save(script);
        } else {
            updateById(script);
        }
    }

    @Override
    public void removeSuiteCaseScript(Long suiteId, Long caseId) {
        baseMapper.physicalDeleteBySuiteCase(suiteId, caseId);
    }

    /**
     * 取「当前登录用户」实际会用的模型名，用于记录脚本元信息。
     * 注意：本方法可能在 @Async 线程里被调用（convertSuiteStepsAsync），
     * 此时 TenantContext 靠 config/AsyncConfig 的 TaskDecorator 跨线程传递。
     */
    private String currentModelName() {
        return userAiConfigService.resolveForCurrentUser().model();
    }
}
