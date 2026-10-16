package com.yx.test.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT工具类
 * 负责Token的生成、解析、验证
 */
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT Token
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return JWT Token字符串
     */
    public String generateToken(Long userId, Long tenantId, Long roleId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(Map.of(
                        "userId", userId,
                        "tenantId", tenantId,
                        "roleId", roleId
                ))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析Token，获取Claims（载荷）
     *
     * @param token JWT Token字符串
     * @return Claims对象
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从Token中获取用户ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从Token中获取租户ID
     */
    public Long getTenantId(String token) {
        Claims claims = parseToken(token);
        return claims.get("tenantId", Long.class);
    }

    /**
     * 从Token中获取角色ID
     */
    public Long getRoleId(String token) {
        Claims claims = parseToken(token);
        return claims.get("roleId", Long.class);
    }

    /**
     * 验证Token是否有效
     *
     * @param token JWT Token字符串
     * @return true=有效，false=无效或已过期
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            // Token已过期
            return false;
        } catch (Exception e) {
            // Token格式错误、签名不匹配等
            return false;
        }
    }

    /**
     * 判断Token是否已过期
     *
     * @param token JWT Token字符串
     * @return true=已过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return true;
        }
    }


    /**
     * 设置token过期
     */
    public void setTokenExpired(String token) {
        // 实际上并不需要设置token过期，因为token过期是通过设置一个过去的过期时间来实现的
        // 这里只是提供一个方法，实际使用中可以根据需要进行实现

    }
}
