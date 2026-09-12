package com.inknexus.common.exception;

import com.inknexus.common.result.Result;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>所有业务服务通过 Spring Boot 自动装配引入（见 CommonAutoConfiguration），
 * 统一把异常转换成 {@link Result} 结构，保证对外接口返回格式一致。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：携带错误码与提示信息。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败（@Valid）：取第一个字段错误信息。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("参数错误");
        return Result.fail(400, message);
    }

    /** 兜底异常：避免直接抛出 500 堆栈。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        return Result.fail(500, "系统异常：" + e.getMessage());
    }
}
