package com.inknexus.common.exception;

import com.inknexus.common.constant.ErrorCode;
import lombok.Getter;


@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码，语义同 Result.code，通常取 {@link ErrorCode} */
    private final Integer code;

    /** 用统一错误码枚举构造：message 采用枚举默认文案。 */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /** 码取枚举语义、文案现场给：service 层主力构造器（如「库存不足：图书ID x」这类带动态详情的提示）。 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /** 自定义错误码与文案构造：仅限运行时才能确定码的场景（如 Feign 转包透传下游 code），固定码一律走枚举。 */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

}