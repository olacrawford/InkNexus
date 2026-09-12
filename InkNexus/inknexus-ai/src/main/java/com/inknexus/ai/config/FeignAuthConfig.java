package com.inknexus.ai.config;

import com.inknexus.ai.context.UserContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenFeign 配置：每次调用下游服务前，自动把当前用户 ID 写入 X-User-Id 请求头。 */
@Configuration
public class FeignAuthConfig {

    /** 借助 Feign 的 RequestInterceptor，在每次 HTTP 调用时给请求头注入 X-User-Id。 */
    @Bean
    public RequestInterceptor userContextFeignInterceptor() {
        return (RequestTemplate template) -> {
            Long userId = UserContextHolder.getUserId();
            if (userId != null) {
                template.header("X-User-Id", userId.toString());
            }
        };
    }
}