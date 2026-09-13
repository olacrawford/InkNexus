package com.inknexus.common.exception;

import com.inknexus.common.constant.ErrorCode;
import lombok.Getter;

//业务层主动抛出异常
@Getter
//继承RuntimeException：运行时异常，不需要方法上写throws声明，代码里直接throw即可
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

}