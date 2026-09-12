package com.inknexus.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.inknexus.payment.mapper")
@EnableFeignClients(basePackages = "com.inknexus.payment.client")
@SpringBootApplication(scanBasePackages = {"com.inknexus.payment", "com.inknexus.common"})
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
