package com.yx.test.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 报告/截图 URL 签名工具
 *
 * 为什么需要它？
 *   报告和截图是浏览器「直接打开」的资源（前端用 window.open(reportUrl) 或 <img src=...>），
 *   浏览器这样发起的请求带不了自定义请求头，也就没法带 Authorization: Bearer xxx。
 *   所以只能把登录 Token 放到 URL 上：/reports/123.html?token=xxxx
 *
 * 注意：
 *   1. 我们只在「返回给前端的 JSON 里」临时拼接 token，绝不写回数据库
 *      （不然数据库里存的就是带 token 的死链接，会泄露给别人）。
 *   2. 外部地址（比如 OSS 的 http://... ）不做处理，原样返回。
 *   3. 已经带过 token 的也不重复拼接。
 */
@Component
@RequiredArgsConstructor
public class ArtifactUrlSigner {

    private final JwtUtils jwtUtils;

    /**
     * 给报告/截图地址补上登录 token。
     *
     * @param rawUrl 原始地址，形如 /reports/2082030327007195138.html
     * @return 拼好 token 的地址；不需要处理时原样返回
     */
    public String sign(String rawUrl) {
        // 空值不管
        if (rawUrl == null || rawUrl.isEmpty()) {
            return rawUrl;
        }
        // 只处理本地这两类资源，其他（含 http/https 的 OSS 地址）原样返回
        if (!rawUrl.startsWith("/reports/") && !rawUrl.startsWith("/screenshots/")) {
            return rawUrl;
        }
        // 已经带过 token 就不重复拼
        if (rawUrl.contains("token=")) {
            return rawUrl;
        }

        Long userId = TenantContext.getCurrentUserId();
        // 没有登录上下文（例如 Runner 回调线程）时无法签发，原样返回
        if (userId == null) {
            return rawUrl;
        }

        String token = jwtUtils.generateToken(userId,
                TenantContext.getCurrentTenantId(),
                TenantContext.getCurrentRoleId());

        // URL 本来可能带查询串（例如 ?a=1），这时要用 & 连接
        String separator = rawUrl.contains("?") ? "&" : "?";
        return rawUrl + separator + "token=" + token;
    }
}
