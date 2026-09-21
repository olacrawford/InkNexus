package com.inknexus.auth.service;

import com.inknexus.auth.dto.LoginRequest;
import com.inknexus.auth.dto.RegisterRequest;
import com.inknexus.auth.vo.LoginResponse;
import com.inknexus.auth.vo.UserVO;

/**
 * 用户账号业务接口：注册与登录（JWT 签发）。
 */
public interface UserService {

    /**
     * 注册新用户；用户名重复时拒绝
     * @throws com.inknexus.common.exception.BusinessException 400 用户名已存在
     */
    UserVO register(RegisterRequest request);

    /**
     * 登录校验并签发 JWT
     * @throws com.inknexus.common.exception.BusinessException 400 用户不存在或密码错误
     */
    LoginResponse login(LoginRequest request);
}
