package com.inknexus.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用响应
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    SYSTEM_ERROR(500, "系统异常"),

    // 业务错误
    ORDER_PLACE_FAILED(400, "下单失败，请检查图书信息"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试");

    private final Integer code;
    private final String message;
}
