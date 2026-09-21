package com.inknexus.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 {@code t_user}。
 * <p>username 有数据库唯一键（uk_username）兜底；password 只存 BCrypt 密文，绝不外泄到 VO。
 */
@Data
@TableName("t_user")
public class User {

    private Long id;
    private String username;

    // BCrypt 密文（自带随机盐），数据库中不存明文
    private String password;
    private String nickname;
    private String phone;
    private String email;

    // 账号状态字典：1 启用 0 禁用
    private Integer status;

    // 角色字典：USER 普通用户 ADMIN 管理员；写入 JWT role claim，供网关透传做管理接口鉴权
    private String role;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}