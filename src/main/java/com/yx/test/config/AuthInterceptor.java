package com.yx.test.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yx.test.common.Result;
import com.yx.test.util.JwtUtils;
import com.yx.test.util.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器 - 每个请求到达 Controller 之前，先验证 Token
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtUtils jwtUtil;
    private final ObjectMapper objectMapper;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 彩蛋：给每个经过拦截器的响应都悄悄盖一枚作者签名。
        // 它不进业务、不打日志，F12 打开 Network 看响应头才能发现。
        response.setHeader("X-Author", "liangzixuan");

        // 1.先尝试从请求头 Authorization 取值（Ajax/fetch 请求走这条路）
        String authHeader = request.getHeader("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // 去掉 "Bearer " 前缀（7个字符），得到纯 Token
            token = authHeader.substring(7);
        }

        // 2.如果请求头里没有，再尝试从 URL 参数里取（?token=xxx）
        //   为什么需要这个？因为浏览器「直接打开」的地址（window.open、<img src>、点击链接）
        //   是由浏览器自己发起的，我们无法给它加自定义请求头，只能把 Token 挂在 URL 上。
        //   典型场景：打开测试报告 /reports/123.html?token=xxx、显示截图 /screenshots/123.png?token=xxx
        if (token == null || token.isEmpty()) {
            token = request.getParameter("token");
        }

        if (token == null || token.isEmpty()) {
            writeError(response, 401, "未登录，请先登录");
            return false;
        }

        // 3. 验证 Token 是否有效（签名正确 + 未过期）

        if(!jwtUtil.validateToken(token)){
            writeError(response, 401,"Token已过期，请重新登录");
            return false;
        }


        // 4. Token 有效，解析用户信息，存入 TenantContext
        Claims claims = jwtUtil.parseToken(token);
        Long userId = claims.get("userId", Long.class);
        Long tenantId = claims.get("tenantId", Long.class);
        Long roleId = claims.get("roleId", Long.class);
        TenantContext.setCurrentUserId(userId, tenantId, roleId);

        return true;//放行


        }

    /** 请求结束后清理 TenantContext */

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        TenantContext.remove();
    }

    /** 往响应汇总写入错误JSON */
    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(code,message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
