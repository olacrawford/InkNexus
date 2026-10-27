package com.inknexus.common.exception;

import com.inknexus.common.constant.ErrorCode;
import com.inknexus.common.result.Result;
import com.inknexus.common.trace.TraceIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：携带错误码与提示信息。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：code={}, message={}", e.getCode(), e.getMessage());
        return new Result<>(e.getCode(), e.getMessage(), null);
    }

    /** 参数校验失败（@Valid）：取第一个字段错误信息。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("参数错误");
        log.warn("参数校验失败：{}", message);
        return new Result<>(ErrorCode.PARAM_ERROR.getCode(), message, null);
    }

    /** 路径/请求参数类型不匹配（如 /books/abc）：按参数错误返回 400，不落进 500 兜底。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误：name={}, value={}", e.getName(), e.getValue());
        return new Result<>(ErrorCode.PARAM_ERROR.getCode(), "参数类型错误：" + e.getName(), null);
    }

    /** 兜底异常：记录完整堆栈，响应不暴露内部信息，只给 traceId 便于关联日志。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        log.error("系统异常 traceId={}", traceId, e);
        return new Result<>(ErrorCode.SYSTEM_ERROR.getCode(), "系统繁忙，请稍后重试（traceId: "
                + (traceId == null ? "-" : traceId) + "）", null);
    }
}
