package com.yx.test.service;

import java.util.Map;

/**
 * 执行失败的「AI 诊断」服务 —— 把 Midscene 抛出的报错交给大模型，翻译成人能看懂的话。
 *
 * <p><b>为什么是「按需」而不是失败后自动跑？</b>
 * 系统规定必须使用「当前登录用户自己的 API Key」，没有全局兜底。
 * 也就是说每问一次 AI，花掉的就是用户自己的 token 额度。
 * 而实际报错里相当一部分本来就是人话（例如「当前页面为登录界面，未找到某按钮」），
 * 并不需要 AI 解释。所以只在用户主动点击时才调用，结果缓存起来避免重复花钱。</p>
 */
public interface ExecutionAiAnalysisService {

    /**
     * 对一条失败的执行记录做 AI 诊断。
     *
     * <p>结果会缓存进该记录的 {@code result_detail.aiAnalysis}，
     * 下次再调用直接返回缓存（返回体里 {@code cached=true}），不再消耗 AI 额度。</p>
     *
     * @param executionId 执行记录ID
     * @return 诊断结果，包含 executionId / cached / model / analyzedAt / errorMessage / analysis
     */
    Map<String, Object> analyzeFailure(Long executionId);
}
