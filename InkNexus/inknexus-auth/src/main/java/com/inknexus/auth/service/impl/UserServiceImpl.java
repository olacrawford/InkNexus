package com.inknexus.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inknexus.auth.dto.LoginRequest;
import com.inknexus.auth.dto.RegisterRequest;
import com.inknexus.auth.entity.User;
import com.inknexus.auth.mapper.UserMapper;
import com.inknexus.auth.service.UserService;
import com.inknexus.auth.util.JwtUtil;
import com.inknexus.auth.vo.LoginResponse;
import com.inknexus.auth.vo.UserVO;
import com.inknexus.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil;

    //构造方法注入
    public UserServiceImpl(UserMapper userMapper,
                           @Value("${jwt.secret}") String secret,
                           @Value("${jwt.expire-seconds}") long expireSeconds) {
        this.userMapper = userMapper;
        this.jwtUtil = new JwtUtil(secret, expireSeconds);
    }

    /**
     * 用户注册
     */
    @Override
    public UserVO register(RegisterRequest request) {

        User exists = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (exists != null) {
            throw new BusinessException(400, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        // 认证服务只保存密码密文，避免明文落库。
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getPhone(), user.getEmail());
    }

    /**
     * 用户登录：校验账号密码，生成JWT令牌
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new BusinessException(400, "用户不存在");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(400, "密码错误");
        }

        String token = jwtUtil.generateToken(user);
        return new LoginResponse(token, user);
    }
}
