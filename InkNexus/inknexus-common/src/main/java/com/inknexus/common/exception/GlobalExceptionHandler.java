package com.inknexus.common.exception;

import com.inknexus.common.result.Result;
import com.inknexus.common.trace.TraceIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器。
 *
 * <p>业务服务的 @SpringBootApplication 通过
 * {@code scanBasePackages = {"com.inknexus.<模块>", "com.inknexus.common"}} 扫描引入本类，
 * 统一把异常转换成 {@link Result} 结构，保证对外接口返回格式一致。</p>
 *
 * <p>所有分支都会落日志：业务/校验异常 warn 即可，未知异常 error 带完整堆栈，
 * 响应只返回 traceId 不泄漏内部信息（堆栈、SQL、类名等），凭 traceId 关联日志排查。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：携带错误码与提示信息。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败（@Valid）：取第一个字段错误信息。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("参数错误");
        log.warn("参数校验失败：{}", message);
        return Result.fail(400, message);
    }

    /** 路径/请求参数类型不匹配（如 /books/abc）：按参数错误返回 400，不落进 500 兜底。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误：name={}, value={}", e.getName(), e.getValue());
        return Result.fail(400, "参数类型错误：" + e.getName());
    }

    /** 兜底异常：记录完整堆栈，响应不暴露内部信息，只给 traceId 便于关联日志。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        log.error("系统异常 traceId={}", traceId, e);
        return Result.fail(500, "系统繁忙，请稍后重试（traceId: "
                + (traceId == null ? "-" : traceId) + "）");
    }
}
