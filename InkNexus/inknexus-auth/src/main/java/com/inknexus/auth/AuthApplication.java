package com.inknexus.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.inknexus.auth.mapper")
@SpringBootApplication(scanBasePackages = {"com.inknexus.auth", "com.inknexus.common"})
public class AuthApplication {

    public static void main(String[] args) {

        SpringApplication.run(AuthApplication.class, args);
    }
}