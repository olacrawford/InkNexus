package com.inknexus.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注册请求体（POST /auth/register）。
 * <p>用户名全局唯一；昵称/手机号/邮箱为可选项。
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String nickname;
    private String phone;
    private String email;

}