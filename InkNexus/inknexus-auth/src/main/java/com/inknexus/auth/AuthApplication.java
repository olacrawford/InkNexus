package com.inknexus.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证服务启动类（端口 8060）。
 * <p>业务链路位置：网关之后第一个处理注册/登录的服务，登录成功后签发 JWT，
 * 网关持同一密钥验签并转换为 {@code X-User-Id}/{@code X-User-Role} 传给下游。
 * <p>scanBasePackages 额外纳入 {@code com.inknexus.common}，以装配公共的全局异常处理、
 * TraceId 过滤器等组件。
 */
@MapperScan("com.inknexus.auth.mapper")
@SpringBootApplication(scanBasePackages = {"com.inknexus.auth", "com.inknexus.common"})
public class AuthApplication {

    public static void main(String[] args) {

        SpringApplication.run(AuthApplication.class, args);
    }
}