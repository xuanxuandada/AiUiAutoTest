package com.yx.test.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yx.test.common.BizException;
import com.yx.test.dto.AiRuntimeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 大模型对话调用客户端（OpenAI 兼容协议：OpenAI / DeepSeek / 通义千问 / 本地 Ollama 等）
 *
 * <p><b>为什么单独抽成组件？</b>
 * 这段 HTTP 调用原本写在 AiGenerationServiceImpl 里，而且是 private 方法，
 * 只有「AI 生成用例」能用。后来「执行失败后问 AI 是什么报错」也要调模型，
 * 如果再复制一份，就会出现两处实现各自演化（比如一处改了超时、一处忘了改）。
 * 所以把「怎么调模型」统一收口到本类，谁需要谁注入。</p>
 *
 * <p><b>关于 API Key：</b>本类不负责解析 Key，只负责「配置给我、我就发请求」。
 * 调用方必须自己用 {@code userAiConfigService.requireForCurrentUser()} 取配置，
 * 以保证永远使用「当前登录用户自己的 Key」，系统不存在全局兜底 Key。</p>
 *
 * <p><b>关于异常类型：</b>失败一律抛 {@link BizException}，不能抛普通 RuntimeException。
 * 因为 GlobalExceptionHandler 对 RuntimeException 做了统一兜底，会把提示改写成
 * 「系统异常，请联系管理员」——那样「Key 无效」「接口地址没配」这类真正有用的信息
 * 就传不到前端了。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiChatClient {

    private final ObjectMapper objectMapper;

    /** 默认采样温度：0.7 在稳定性与表达多样性之间取平衡 */
    private static final double DEFAULT_TEMPERATURE = 0.7;

    /** 默认最大输出 token 数 */
    private static final int DEFAULT_MAX_TOKENS = 4096;

    /**
     * 发一次对话请求，返回模型输出的正文（使用默认温度与最大长度）。
     *
     * @param aiConfig     本次要用的 AI 配置（来自当前登录用户：地址/Key/模型/超时）
     * @param systemPrompt 系统提示词：给模型定角色、定输出格式
     * @param userMessage  用户消息：本次要处理的具体内容
     * @return 模型返回的正文文本（choices[0].message.content）
     */
    public String chat(AiRuntimeConfig aiConfig, String systemPrompt, String userMessage) {
        return chat(aiConfig, systemPrompt, userMessage, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS);
    }

    /**
     * 发一次对话请求（可自定义温度与最大输出长度）。
     *
     * @param temperature 采样温度，越低越确定、越高越发散
     * @param maxTokens   最大输出 token 数
     */
    @SuppressWarnings("unchecked")
    public String chat(AiRuntimeConfig aiConfig, String systemPrompt, String userMessage,
                       double temperature, int maxTokens) {
        // 这两道是「兜底校验」：正常调用方都已经用 requireForCurrentUser() 过了关，
        // 这里再拦一次，防止有人绕过校验直接调用本方法。
        if (aiConfig == null || !aiConfig.hasApiKey()) {
            throw BizException.internalError("未获取到可用的AI API Key，无法调用AI服务。"
                    + "请先到【设置 → AI模型配置】保存你自己的 API Key");
        }
        if (aiConfig.apiUrl() == null || aiConfig.apiUrl().isBlank()) {
            throw BizException.internalError("AI接口地址未配置，无法调用AI服务");
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(aiConfig.timeout()))
                    .build();

            Map<String, Object> requestBody = Map.of(
                    "model", aiConfig.model(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", temperature,
                    "max_tokens", maxTokens
            );

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(aiConfig.apiUrl()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(aiConfig.timeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            requestBuilder.header("Authorization", "Bearer " + aiConfig.apiKey());

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("AI API调用失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                throw BizException.internalError("AI API调用失败: " + response.statusCode());
            }

            // 解析 OpenAI 格式响应：choices[0].message.content
            Map<String, Object> responseMap = objectMapper.readValue(response.body(), new TypeReference<>() {});
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }

            throw BizException.internalError("AI API响应格式异常");
        } catch (BizException e) {
            // 上面主动抛的业务异常原样往外传，避免被下面的 catch (Exception) 又包一层，
            // 那样错误前缀会变成「调用AI API异常: AI API调用失败: 401」，看着很绕。
            throw e;
        } catch (Exception e) {
            log.error("调用AI API异常", e);
            throw BizException.internalError("调用AI API异常: " + e.getMessage());
        }
    }
}
