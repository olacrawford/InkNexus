package com.inknexus.auth.vo;

import com.inknexus.auth.entity.User;
import lombok.Data;

/**
 * 登录成功响应体（POST /auth/login 返回载荷）。
 * <p>token 为服务端签发的 JWT；前端后续请求携带它，由网关验签后转换为
 * {@code X-User-Id} / {@code X-User-Role} 传给下游服务。
 */
@Data
public class LoginResponse {

    // 签发的 JWT（HS256），载荷含 userId/username/nickname/role
    private String token;
    private Long userId;
    private String username;
    private String nickname;

    public LoginResponse() {
    }

    public LoginResponse(String token, User user) {
        this.token = token;
        this.userId = user.getId();
        this.username = user.getUsername();
        this.nickname = user.getNickname();
    }

}