package com.inknexus.auth.vo;

import lombok.Data;

//用户对外展示对象（不含密码等敏感字段）。
//作为注册接口的返回载荷，也内嵌在部分响应中对外暴露用户基本信息。
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;

    public UserVO() {
    }

    public UserVO(Long id, String username, String nickname, String phone, String email) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.phone = phone;
        this.email = email;
    }

}
