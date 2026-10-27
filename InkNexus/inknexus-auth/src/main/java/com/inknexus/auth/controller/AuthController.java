package com.inknexus.auth.controller;

import com.inknexus.auth.dto.LoginRequest;
import com.inknexus.auth.dto.RegisterRequest;
import com.inknexus.auth.service.UserService;
import com.inknexus.auth.vo.LoginResponse;
import com.inknexus.auth.vo.UserVO;
import com.inknexus.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证入口接口（/auth）：注册、登录与健康检查。
 * <p>/auth/login、/auth/register、/auth/hello 是网关放行的公开接口（无需 JWT）；
 * 登录返回的 JWT 在后续请求中由网关校验，并转换为 {@code X-User-Id}/{@code X-User-Role} 传给下游服务。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("inknexus-auth is running");
    }

    /**
     * 用户注册：用户名唯一，密码以 BCrypt 密文落库
     * @throws com.inknexus.common.exception.BusinessException 400 用户名已存在
     */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    /**
     * 用户登录：账号密码校验通过后签发 JWT
     * @throws com.inknexus.common.exception.BusinessException 400 用户不存在或密码错误
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

}