package com.inknexus.ai.config;

import com.inknexus.ai.context.UserContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web MVC 配置：注册 UserContextInterceptor，让它对经过本服务的请求生效。 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 未指定拦截路径，默认拦截所有请求；如需只拦 AI 接口，可写成 registry.addInterceptor(...).addPathPatterns("/ai/**")
        registry.addInterceptor(new UserContextInterceptor());
    }
}