package com.inknexus.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 助手微服务（inknexus-ai，端口 8071）启动类。
 * 只读智能助手：Spring AI + DeepSeek 实现对话，经 OpenFeign 只读调用 book / order 服务，自身不落业务库；
 * 启动前需设置 DEEPSEEK_API_KEY 环境变量供模型配置读取。
 */
@SpringBootApplication(scanBasePackages = {"com.inknexus.ai", "com.inknexus.common"})
@EnableFeignClients
public class AiAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiAssistantApplication.class, args);
    }
}
