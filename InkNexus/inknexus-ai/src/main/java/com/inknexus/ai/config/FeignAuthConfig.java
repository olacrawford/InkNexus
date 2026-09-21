package com.inknexus.ai.config;

import com.inknexus.ai.context.UserContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenFeign 配置：每次调用下游服务前，自动把当前用户 ID 写入 X-User-Id 请求头。
 * <p>这是“对话查订单”安全链路的最后一跳：网关校验 JWT 后注入 X-User-Id →
 * {@link com.inknexus.ai.context.UserContextInterceptor} 写入 ThreadLocal →
 * 这里从 {@link com.inknexus.ai.context.UserContextHolder} 取出再塞进 Feign 请求头 →
 * order 服务只按该 ID 查询并校验数据归属。模型在对话中无法指定 userId，因此查不到他人订单。
 */
@Configuration
public class FeignAuthConfig {

    /** 每次调用下游前注入 X-User-Id；userId 为 null（无请求上下文）时不注入，交由下游按未登录处理。 */
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