package com.inknexus.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类（端口 8050）：下单主链、订单状态机、MQ 事件收发与超时关单兜底任务所在服务。
 */
@MapperScan("com.inknexus.order.mapper")
@EnableFeignClients(basePackages = "com.inknexus.order.client")
@EnableScheduling
// 同时扫描 com.inknexus.common，装配公共组件（统一返回/异常、链路追踪、MQ 常量等）
@SpringBootApplication(scanBasePackages = {"com.inknexus.order", "com.inknexus.common"})
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
