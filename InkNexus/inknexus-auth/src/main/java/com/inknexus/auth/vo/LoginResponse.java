package com.inknexus.auth.vo;

import com.inknexus.auth.entity.User;
import lombok.Data;

@Data
public class LoginResponse {

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