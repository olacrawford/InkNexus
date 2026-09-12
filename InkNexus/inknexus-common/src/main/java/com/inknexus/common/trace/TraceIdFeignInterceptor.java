package com.inknexus.common.trace;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

/**
 * Feign 调用透传 TraceId：把 MDC 里的链路编号写进下游请求头，
 * 让「网关 → 订单 → 库存」这类跨服务调用在日志里是同一个编号。
 *
 * <p>只放类不放 @Component，由 {@link TraceIdFeignConfig} 按 classpath 条件注册，
 * 避免 book/auth/stock 这类没有 Feign 的服务启动报类缺失。</p>
 */
public class TraceIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if (traceId != null && !traceId.isBlank()) {
            template.header(TraceIdFilter.TRACE_ID_HEADER, traceId);
        }
    }
}
