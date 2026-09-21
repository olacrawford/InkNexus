package com.inknexus.cart;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 购物车服务启动类（端口 8083）。
 * <p>业务链路位置：登录用户加购/结算的中转站——加购时通过 BookClient 调图书服务校验，
 * 下单时订单服务通过 Feign 调 /cart/selected 取已勾选条目。
 * <p>{@code scanBasePackages} 额外纳入 {@code com.inknexus.common} 以装配公共组件。
 */
@MapperScan("com.inknexus.cart.mapper")
@EnableFeignClients(basePackages = "com.inknexus.cart.client")
@SpringBootApplication(scanBasePackages = {"com.inknexus.cart", "com.inknexus.common"})
public class CartApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartApplication.class, args);
    }
}
