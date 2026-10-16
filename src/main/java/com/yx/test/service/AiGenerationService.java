package com.yx.test.service;

import com.yx.test.entity.TestCase;

import java.util.List;
import java.util.Map;

/**
 * AI测试用例生成服务
 * 通过大模型将自然语言描述转换为可执行的Midscene.js测试步骤
 */
public interface AiGenerationService {

    /**
     * 根据自然语言描述生成测试用例
     *
     * @param prompt     用户的自然语言描述，如"测试用户登录功能"
     * @param targetUrl  目标测试URL
     * @return 生成的测试用例列表
     */
    List<TestCase> generateTestCases(String prompt, String targetUrl);

    /**
     * 根据页面描述生成完整的测试场景
     *
     * @param pageDescription 页面功能描述
     * @param targetUrl       目标URL
     * @param caseCount       期望生成的用例数量
     * @return 生成的测试用例列表
     */
    List<TestCase> generateScenario(String pageDescription, String targetUrl, int caseCount);

    /**
     * 将自然语言步骤通过AI分类转换为Midscene.js可执行格式
     * 每个步骤Map包含 step(自然语言描述) 和 expected(预期结果，可为空)
     * AI会将expectedResult放到转换后正确的步骤上
     *
     * @param stepsWithExpected 步骤列表，如[{"step":"输入admin","expected":"输入框显示admin"}]
     * @return Midscene.js格式的步骤列表
     */
    List<Map<String, String>> classifySteps(List<Map<String, String>> stepsWithExpected);
}
