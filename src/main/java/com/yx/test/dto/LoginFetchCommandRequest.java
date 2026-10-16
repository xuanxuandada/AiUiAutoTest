package com.yx.test.dto;

import lombok.Data;

@Data
public class LoginFetchCommandRequest {
    /** 登录态提取命令 */
    private String logintext;

    /** 是否启用登录态: 1-启用, 0-禁用 */
    private Integer loginEnabled;
}
