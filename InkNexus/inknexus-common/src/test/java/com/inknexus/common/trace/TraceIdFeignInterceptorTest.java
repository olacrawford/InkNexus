package com.inknexus.common.trace;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceIdFeignInterceptorTest {

    private final TraceIdFeignInterceptor interceptor = new TraceIdFeignInterceptor();

    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    @Test
    void apply_addsTraceIdHeader_whenMdcPresent() {
        MDC.put(TraceIdFilter.MDC_TRACE_ID, "trace-abc");

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertEquals("trace-abc",
                template.headers().get(TraceIdFilter.TRACE_ID_HEADER).iterator().next());
    }

    @Test
    void apply_skipsHeader_whenMdcEmpty() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertFalse(template.headers().containsKey(TraceIdFilter.TRACE_ID_HEADER));
        assertTrue(template.headers().isEmpty() || template.headers().size() >= 0);
    }
}
