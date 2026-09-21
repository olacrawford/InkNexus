package com.inknexus.ai.config;

import com.inknexus.ai.context.UserContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web MVC 配置：注册 UserContextInterceptor，让它对经过本服务的请求生效。
 * 拦截器在请求进入时从网关注入的 X-User-Id 恢复用户上下文、请求结束时清理，
 * 是“对话查订单”用户绑定链路在本服务的入口。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserContextInterceptor());
    }
}