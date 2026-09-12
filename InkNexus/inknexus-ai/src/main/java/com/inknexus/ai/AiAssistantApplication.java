package com.inknexus.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** AI 助手微服务启动类。 */
@SpringBootApplication(scanBasePackages = {"com.inknexus.ai", "com.inknexus.common"})
@EnableFeignClients // 开启 OpenFeign 扫描，让 BookFeignClient / OrderFeignClient 可被注入
public class AiAssistantApplication {
    public static void main(String[] args) {
        // Spring Boot 标准入口：启动内嵌 Tomcat 并加载 Spring 容器
        SpringApplication.run(AiAssistantApplication.class, args);
    }
}
