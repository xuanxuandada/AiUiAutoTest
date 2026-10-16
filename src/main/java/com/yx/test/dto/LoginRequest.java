package com.yx.test.dto;

import lombok.Data;

/**
 * 登录请求DTO
 */
@Data
public class LoginRequest {

    /** 用户名 */
    private String name;

    /** 密码 */
    private String password;

    /** 用户选择的租户ID（登录时传入） */
    private Long tenantId;
}
