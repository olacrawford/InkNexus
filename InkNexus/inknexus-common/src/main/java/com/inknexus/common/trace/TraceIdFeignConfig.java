package com.inknexus.common.trace;

import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TraceId 的 Feign 透传注册：classpath 上有 Feign 时才加载（book/auth/stock 没有 Feign 依赖）。
 * Spring Cloud OpenFeign 会自动收集容器里所有 RequestInterceptor，应用到本服务的全部 Feign 客户端。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RequestInterceptor.class)
public class TraceIdFeignConfig {

    @Bean
    @ConditionalOnMissingBean(TraceIdFeignInterceptor.class)
    public TraceIdFeignInterceptor traceIdFeignInterceptor() {
        return new TraceIdFeignInterceptor();
    }
}
