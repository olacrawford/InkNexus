package com.inknexus.auth.service.impl;

import com.inknexus.auth.dto.LoginRequest;
import com.inknexus.auth.dto.RegisterRequest;
import com.inknexus.auth.entity.User;
import com.inknexus.auth.mapper.UserMapper;
import com.inknexus.auth.util.JwtUtil;
import com.inknexus.auth.vo.LoginResponse;
import com.inknexus.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String SECRET = "inknexus-auth-secret-key-20260827-0123456789";

    @Mock
    private UserMapper userMapper;

    private UserServiceImpl userService;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 3600);
        userService = new UserServiceImpl(userMapper, SECRET, 3600);
    }

    @Test
    void login_returnsTokenAndUserId_whenPasswordMatches() {
        User user = new User();
        user.setId(7L);
        user.setUsername("tom");
        user.setNickname("Tom");
        user.setPassword(new BCryptPasswordEncoder().encode("123456"));

        when(userMapper.selectOne(any())).thenReturn(user);

        LoginResponse response = userService.login(loginRequest("tom", "123456"));

        assertNotNull(response.getToken());
        assertEquals(7L, response.getUserId());
        assertEquals("tom", response.getUsername());
        assertEquals("7", jwtUtil.parseToken(response.getToken()).getSubject());
        verify(userMapper).selectOne(any());
    }

    @Test
    void login_throws_whenUserNotFound() {
        when(userMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.login(loginRequest("missing", "123456")));

        assertEquals(400, exception.getCode());
    }

    @Test
    void login_throws_whenPasswordIncorrect() {
        User user = new User();
        user.setId(7L);
        user.setUsername("tom");
        user.setPassword(new BCryptPasswordEncoder().encode("other-password"));

        when(userMapper.selectOne(any())).thenReturn(user);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.login(loginRequest("tom", "123456")));

        assertEquals(400, exception.getCode());
    }

    @Test
    void register_throws_whenUsernameExists() {
        User exists = new User();
        exists.setId(1L);
        exists.setUsername("tom");

        when(userMapper.selectOne(any())).thenReturn(exists);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("tom");
        request.setPassword("123456");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.register(request));

        assertEquals(400, exception.getCode());
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
