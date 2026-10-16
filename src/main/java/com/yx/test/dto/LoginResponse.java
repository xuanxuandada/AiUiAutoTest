package com.yx.test.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** JWT Token */
    private String token;

    /** 用户ID（字符串格式，避免JS精度丢失） */
    @JsonProperty("userId")
    private String userIdStr;

    /** 租户ID（字符串格式，避免JS精度丢失） */
    @JsonProperty("tenantId")
    private String tenantIdStr;

    /** 角色ID（字符串格式，避免JS精度丢失） */
    @JsonProperty("roleId")
    private String roleIdStr;

    /** 用户名 */
    private String name;

    public LoginResponse(String token, Long userId, Long tenantId, Long roleId, String name) {
        this.token = token;
        this.userIdStr = String.valueOf(userId);
        this.tenantIdStr = String.valueOf(tenantId);
        this.roleIdStr = String.valueOf(roleId);
        this.name = name;
    }
}
