package com.inknexus.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inknexus.auth.dto.LoginRequest;
import com.inknexus.auth.dto.RegisterRequest;
import com.inknexus.auth.entity.User;
import com.inknexus.auth.mapper.UserMapper;
import com.inknexus.auth.service.UserService;
import com.inknexus.auth.vo.LoginResponse;
import com.inknexus.auth.vo.UserVO;
import com.inknexus.common.constant.ErrorCode;
import com.inknexus.common.exception.BusinessException;
import com.inknexus.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;

/**
 * 用户注册/登录业务实现。
 * <p>密码使用 BCrypt 单向加密（每次 encode 自动加盐，校验用 matches）；
 * JWT 密钥与过期时间来自 Nacos 配置 {@code auth.yaml} 的 {@code jwt.secret}/{@code jwt.expire-seconds}，
 * 网关侧配置同一密钥才能验签。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expire-seconds}")
    private long expireSeconds;
    private JwtUtil jwtUtil;

    /** 注入完成后用配置构建 JwtUtil（原构造方法里的派生初始化，包私有便于单测直接调用） */
    @PostConstruct
    void initJwt() {
        jwtUtil = new JwtUtil(secret, expireSeconds);
    }

    /**
     * 用户注册：先查重再插入，并发同名注册存在检查窗口，最终由数据库唯一键 uk_username 兜底；
     * 单条 insert 原子操作，无显式事务
     * @throws BusinessException 400 用户名已存在
     */
    @Override
    public UserVO register(RegisterRequest request) {

        User exists = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (exists != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        // 认证服务只保存密码密文，避免明文落库。
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        // status=1 表示启用（字典：1 启用 0 禁用）；role 未显式赋值，由数据库默认值 'USER' 兜底
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getPhone(), user.getEmail());
    }

    /**
     * 用户登录：校验账号密码后签发 JWT
     * @throws BusinessException 400 用户不存在或密码错误
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户不存在");
        }

        // BCrypt 密文无法逆明文，只能用 matches 对比
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
        return new LoginResponse(token, user);
    }
}
