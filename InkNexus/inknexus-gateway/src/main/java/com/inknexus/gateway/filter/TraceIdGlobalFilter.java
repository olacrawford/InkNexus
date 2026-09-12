package com.inknexus.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * TraceId 链路过滤器：为每个进入网关的请求生成/透传 X-Trace-Id，
 * 下游业务服务读取该头写入 MDC，一个请求跨服务的日志用同一个编号串联。
 * order=-200，先于鉴权过滤器(-100)执行。
 *
 * <p>请求头里已有的 X-Trace-Id 原样透传（外部调用方可能自带链路编号，保留其上下文；
 * 该头只用于日志关联，不涉及权限，无需像 X-User-Id 那样强制覆盖）。</p>
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        String traceId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : incoming;

        // 响应头回写，前端或联调方报障时可以直接给出这个编号
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, traceId)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
