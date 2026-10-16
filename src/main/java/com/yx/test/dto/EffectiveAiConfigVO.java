package com.yx.test.dto;

/**
 * 「当前实际生效」的 AI 配置来源（排障用，不含 Key）
 *
 * <p>对应接口：GET /api/user/ai-config/effective</p>
 *
 * <p>作用：当用户觉得「我明明配了 / 我没配为什么能用」时，
 * 调这个接口就能立刻看清后端这次到底用的是谁的配置。</p>
 *
 * <p>因为系统规定「必须用用户自己的 Key、没有全局兜底」，
 * 所以 {@code source} 只有两种取值：</p>
 * <ul>
 *   <li>{@code USER_CONFIG}：用的是当前登录用户自己的 Key，AI 功能可用</li>
 *   <li>{@code NONE}：该用户还没配 Key，AI 功能不可用（需去配置页补上）</li>
 * </ul>
 *
 * @param source          来源说明：USER_CONFIG / NONE
 * @param model           生效的模型名
 * @param modelFamily     生效的 Midscene 模型族
 * @param baseUrl         生效的接口基址
 * @param timeout         生效的超时秒数
 * @param apiKeyReady     当前是否具备可用的 Key（只回答「有没有」，不返回 Key 本身）
 */
public record EffectiveAiConfigVO(
        String source,
        String model,
        String modelFamily,
        String baseUrl,
        int timeout,
        boolean apiKeyReady
) {
}
