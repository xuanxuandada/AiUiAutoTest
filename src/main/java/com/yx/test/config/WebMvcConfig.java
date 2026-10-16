package com.yx.test.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc配置类  —— 注册拦截器，指定拦截/放行
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor; // Changed from private to public

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 拦截接口 + 报告/截图。
                // 报告和截图以前是「静态资源直出」的，没人管，只要猜到文件名就能下载别人的报告；
                // 现在它们也走拦截器，改由 ArtifactController 校验归属后再输出文件。
                .addPathPatterns("/api/**", "/reports/**", "/screenshots/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/verify",
                        "/api/auth/tenants",
                        "/api/register",
                        "/api/executions/callback"); // 放行登录接口 + Runner 回调
    }
}
