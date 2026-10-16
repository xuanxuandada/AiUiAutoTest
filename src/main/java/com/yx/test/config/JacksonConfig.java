package com.yx.test.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置
 * 1. 将所有 Long 类型序列化为字符串，防止前端 JavaScript 精度丢失
 *    （JS 的 Number 最大安全整数为 9007199254740991，雪花ID超出此范围会丢失精度）
 * 2. Runner回传的Playwright HTML报告base64实测约47MB（字符串长度47M+），
 *    而Jackson默认 maxStringLength 仅20MB，导致 /api/executions/callback 解析大请求时
 *    抛 "String value length exceeds the maximum allowed" 异常（HTTP 500）。
 *    这里强制将 HttpMessageConverter 使用的 ObjectMapper 的 StreamReadConstraints 提升到 100MB。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(long.class, ToStringSerializer.instance);
        };
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer streamReadConstraintsCustomizer() {
        return builder -> builder.postConfigurer(objectMapper ->
                objectMapper.getFactory().setStreamReadConstraints(
                        StreamReadConstraints.builder()
                                .maxStringLength(100 * 1024 * 1024)
                                .build()));
    }
}
