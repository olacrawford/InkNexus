package com.inknexus.cart;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.inknexus.cart.mapper")
@EnableFeignClients(basePackages = "com.inknexus.cart.client")
@SpringBootApplication(scanBasePackages = {"com.inknexus.cart", "com.inknexus.common"})
public class CartApplication {

    // 购物车服务启动类：同时开启 MyBatis-Plus Mapper 扫描和 OpenFeign 客户端
    public static void main(String[] args) {
        SpringApplication.run(CartApplication.class, args);
    }
}
