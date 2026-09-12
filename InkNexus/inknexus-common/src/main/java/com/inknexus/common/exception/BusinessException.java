package com.inknexus.common.exception;

import com.inknexus.common.constant.ErrorCode;
import lombok.Getter;

/**
 * 业务层主动抛出的异常：携带错误码与提示信息，
 * 由 {@link GlobalExceptionHandler} 统一捕获并转成 {@link com.inknexus.common.result.Result} 返回。
 */
@Getter
//继承RuntimeException：运行时异常，不需要方法上写throws声明，代码里直接throw即可
public class BusinessException extends RuntimeException {

    /** 错误码，通常来自 {@link ErrorCode#getCode()}，也可自定义业务码 */
    private final Integer code;

    /** 用枚举错误码构造，提示文案取枚举默认 message */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /** 用自定义错误码与文案构造，适合需要携带具体业务信息（如“库存不足”）的场景 */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

}
