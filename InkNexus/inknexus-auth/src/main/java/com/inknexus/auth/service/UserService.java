package com.inknexus.auth.service;

import com.inknexus.auth.dto.LoginRequest;
import com.inknexus.auth.dto.RegisterRequest;
import com.inknexus.auth.vo.LoginResponse;
import com.inknexus.auth.vo.UserVO;

public interface UserService {

    UserVO register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
