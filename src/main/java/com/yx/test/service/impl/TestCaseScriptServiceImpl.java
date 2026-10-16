package com.yx.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yx.test.entity.TestCaseScript;
import com.yx.test.mapper.TestCaseScriptMapper;
import com.yx.test.service.AiGenerationService;
import com.yx.test.service.TestCaseScriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseScriptServiceImpl extends ServiceImpl<TestCaseScriptMapper, TestCaseScript>
        implements TestCaseScriptService {

    private final AiGenerationService aiGenerationService;

    @Override
    public TestCaseScript getByCaseId(Long testCaseId) {
        return getOne(new LambdaQueryWrapper<TestCaseScript>()
                .eq(TestCaseScript::getTestCaseId, testCaseId));
    }

    @Async
    @Override
    public void convertStepsAsync(Long scriptId, Long testCaseId,
                                   List<Map<String, String>> steps,
                                   String targetUrl,
                                   boolean needsClassify) {
        log.info("开始异步转换脚本，scriptId: {}, needsClassify: {}", scriptId, needsClassify);

        TestCaseScript script = new TestCaseScript();
        script.setId(scriptId);  // 使用save后自动生成的ID
        script.setTestCaseId(testCaseId);

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
                log.info("提取的步骤及预期: {}", stepsForAi);

                // AI转换（AI会自己把expectedResult放到正确的步骤上）
                List<Map<String, String>> classifiedSteps = aiGenerationService.classifySteps(stepsForAi);
                log.info("AI转换后的步骤: {}", classifiedSteps);
                finalSteps = new ArrayList<>(classifiedSteps);
            } else {
                // 已经是Midscene.js格式
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
            log.error("AI步骤分类失败，scriptId: {}", scriptId, e);
            script.setScriptSteps(List.of());
            script.setStatus("FAILED");
        }

        // 更新数据库
        updateById(script);
        log.info("脚本转换完成，scriptId: {}, status: {}", scriptId, script.getStatus());
    }
}
