package com.inknexus.book.config;

import com.inknexus.book.filter.AdminOnlyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册图书管理接口的角色校验拦截器。
 * GET 请求在拦截器内部直接放行，因此这里拦截整个 /books/** 即可。
 *
 * <p>拦截器无外部依赖，直接 new 而未注册为 Spring Bean；
 * 网关已做过 JWT 层面的 ADMIN 判权，这里属于服务内的第二层兜底（见 {@link AdminOnlyInterceptor}）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminOnlyInterceptor())
                .addPathPatterns("/books/**");
    }
}
