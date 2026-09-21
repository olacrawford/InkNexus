package com.inknexus.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关服务启动类（端口 8080，Spring Cloud Gateway / WebFlux）：全系统唯一入口，
 * 负责路由、JWT 鉴权与链路编号注入。
 *
 * <p>未指定 scanBasePackages，组件扫描只覆盖 com.inknexus.gateway：
 * 依赖的 inknexus-common 里基于 Servlet 的 TraceIdFilter 与 Feign 配置不会在网关生效，
 * 链路编号由 WebFlux 侧的 {@link com.inknexus.gateway.filter.TraceIdGlobalFilter} 负责。</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
