package com.inknexus.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceIdGlobalFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private TraceIdGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TraceIdGlobalFilter();
    }

    @Test
    void filter_generatesTraceId_whenHeaderMissing() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerHttpRequest downstream = captor.getValue().getRequest();

        String traceId = downstream.getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER);
        assertNotNull(traceId);
        assertFalse(traceId.isBlank());
        // 响应头同步回写，方便前端报障时取号
        assertEquals(traceId, exchange.getResponse().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER));
    }

    @Test
    void filter_reusesIncomingTraceId_whenHeaderPresent() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header(TraceIdGlobalFilter.TRACE_ID_HEADER, "trace-abc")
                        .build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertEquals("trace-abc",
                captor.getValue().getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER));
        assertEquals("trace-abc",
                exchange.getResponse().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER));
    }

    @Test
    void filter_runsBeforeAuthFilter() {
        // 数值越小越先执行：必须早于鉴权过滤器的 -100，401 响应也能带回响应头
        assertTrue(filter.getOrder() < -100);
    }
}
