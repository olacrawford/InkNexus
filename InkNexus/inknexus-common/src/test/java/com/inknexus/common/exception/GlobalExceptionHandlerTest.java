package com.inknexus.common.exception;

import com.inknexus.common.result.Result;
import com.inknexus.common.trace.TraceIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    @Test
    void handleException_returnsTraceId_andHidesInternalMessage() {
        MDC.put(TraceIdFilter.MDC_TRACE_ID, "trace-xyz");

        Result<Void> result = handler.handleException(new IllegalStateException("secret-sql-details"));

        assertEquals(500, result.getCode());
        assertTrue(result.getMessage().contains("trace-xyz"));
        assertFalse(result.getMessage().contains("secret-sql-details"));
    }

    @Test
    void handleException_usesDash_whenTraceIdMissing() {
        Result<Void> result = handler.handleException(new RuntimeException("boom"));

        assertTrue(result.getMessage().contains("traceId: -"));
    }

    @Test
    void handleBusinessException_passesCodeAndMessage() {
        Result<Void> result = handler.handleBusinessException(new BusinessException(409, "库存不足"));

        assertEquals(409, result.getCode());
        assertEquals("库存不足", result.getMessage());
    }

    @Test
    void handleTypeMismatch_returns400WithName() {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", null, new NumberFormatException("abc"));

        Result<Void> result = handler.handleTypeMismatch(exception);

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("id"));
    }

    @Test
    void handleValidationException_returnsFirstFieldMessage() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "title", "书名不能为空"));
        bindingResult.addError(new FieldError("request", "price", "价格不能为空"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new MethodParameter(Object.class.getMethod("hashCode"), -1), bindingResult);

        Result<Void> result = handler.handleValidationException(exception);

        assertEquals(400, result.getCode());
        assertEquals("书名不能为空", result.getMessage());
    }
}
