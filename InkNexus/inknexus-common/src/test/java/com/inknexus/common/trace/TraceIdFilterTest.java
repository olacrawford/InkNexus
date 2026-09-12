package com.inknexus.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    @Test
    void doFilter_generatesTraceId_whenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilter(request, response,
                (req, res) -> traceIdInChain.set(MDC.get(TraceIdFilter.MDC_TRACE_ID)));

        String traceId = traceIdInChain.get();
        assertNotNull(traceId);
        assertEquals(traceId, response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        // 请求结束后 MDC 必须清理，避免容器线程复用串号
        assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID));
    }

    @Test
    void doFilter_reusesIncomingTraceId_whenHeaderPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilter(request, response,
                (req, res) -> traceIdInChain.set(MDC.get(TraceIdFilter.MDC_TRACE_ID)));

        assertEquals("trace-abc", traceIdInChain.get());
        assertEquals("trace-abc", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }
}
