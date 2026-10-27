package com.inknexus.common.context;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 用户上下文的 MVC 装配：注册 {@link CurrentUserInterceptor} 并置于最高优先级，
 * 保证先于各服务自己的拦截器（如 book 的 AdminOnlyInterceptor）执行。
 * <p>与 {@code com.inknexus.common.trace.TraceIdFilter} 同一装配方式：
 * 各服务 {@code scanBasePackages} 扫到 com.inknexus.common 即生效，
 * {@code @ConditionalOnWebApplication(SERVLET)} 保证在 WebFlux 应用（gateway）不加载。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class UserContextWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CurrentUserInterceptor())
                .order(Ordered.HIGHEST_PRECEDENCE);
    }

}
