package com.inknexus.common.exception;

import com.inknexus.common.constant.ErrorCode;
import lombok.Getter;

/**
 * 统一业务异常：业务层校验失败（如库存不足、地址不存在）时主动抛出，
 * 携带错误码与用户可读提示，由 GlobalExceptionHandler 统一转成 {@link com.inknexus.common.result.Result}
 * 失败响应返回，不会落进 500 兜底，日志只记 warn 不打堆栈。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码，语义同 Result.code，通常取 {@link ErrorCode} */
    private final Integer code;

    /** 用统一错误码枚举构造：message 采用枚举默认文案。 */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /** 自定义错误码与文案构造：适合给出具体原因的场景（如 409 + "库存不足"）。 */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

}