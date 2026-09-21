package com.inknexus.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 支付服务启动类（8051）。
 * 当前为内部模拟支付：落支付单后发布 PaySuccess 事件，由订单服务异步把订单置为已支付并确认库存。
 * scanBasePackages 额外引入 com.inknexus.common，装配统一异常处理、Result 封装等公共组件。
 */
@MapperScan("com.inknexus.payment.mapper")
@EnableFeignClients(basePackages = "com.inknexus.payment.client")
@SpringBootApplication(scanBasePackages = {"com.inknexus.payment", "com.inknexus.common"})
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
