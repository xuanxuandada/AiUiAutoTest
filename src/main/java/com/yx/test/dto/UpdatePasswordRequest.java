package com.yx.test.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * 修改密码DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor

public class UpdatePasswordRequest {

    /** 原来的密码 */
    private String oldPassword;

    /** 新的密码 */
    private String newPassword;



}
