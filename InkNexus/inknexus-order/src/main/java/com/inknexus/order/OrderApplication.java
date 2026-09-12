package com.inknexus.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.inknexus.order.mapper")
@EnableFeignClients(basePackages = "com.inknexus.order.client")
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.inknexus.order", "com.inknexus.common"})
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
