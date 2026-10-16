package com.yx.test.dto;

/**
 * 「本次运行时」解析出来的 AI 配置（一次请求一份，不共享、不缓存）
 *
 * <p>为什么不直接用 {@code UserAiConfig} 实体？因为有两个使用方对 URL 的语义要求不同：</p>
 * <ul>
 *   <li>Java 后端调用大模型走的是 OpenAI 兼容的 chat/completions 接口，需要<b>完整地址</b>，
 *       例如 {@code https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}</li>
 *   <li>Midscene（Node 侧）的 {@code MIDSCENE_MODEL_BASE_URL} 需要的是<b>基址</b>，
 *       即 {@code https://dashscope.aliyuncs.com/compatible-mode/v1}（不含 /chat/completions）</li>
 * </ul>
 * <p>所以数据库里统一只存「基址」，由本对象同时派生出 baseUrl 和 apiUrl，
 * 让两个使用方各取所需，避免出现 404。</p>
 *
 * <p>注意：{@code apiKey} 永远是「当前登录用户自己」的 Key，没有任何全局兜底。
 * 用户没配 Key 时它是 null，{@code hasApiKey()} 为 false，调用方必须拒绝发起 AI 调用。</p>
 *
 * @param baseUrl          AI接口基址（给 Midscene 用，不含 /chat/completions）
 * @param apiUrl           完整 chat/completions 地址（给 Java 后端 HttpClient 用）
 * @param apiKey           本次实际要使用的 API Key（只能是当前登录用户自己的；可能为 null）
 * @param model            模型名称，如 qwen-vl-max
 * @param modelFamily      Midscene 模型族，如 qwen2.5-vl
 * @param timeout          超时秒数
 */
public record AiRuntimeConfig(
        String baseUrl,
        String apiUrl,
        String apiKey,
        String model,
        String modelFamily,
        int timeout
) {

    /** 是否拿到了可用的 Key（没 Key 就不该发起 AI 调用） */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
