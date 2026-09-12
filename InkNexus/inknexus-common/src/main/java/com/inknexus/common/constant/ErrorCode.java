package com.inknexus.common.constant;

import lombok.Getter;

/**
 * 全局统一错误码枚举：code 尽量对齐 HTTP 语义，message 是给前端展示的默认提示。
 */
@Getter
public enum ErrorCode {
    /** 成功 */
    SUCCESS(200, "success"),
    /** 通用参数错误 */
    PARAM_ERROR(400, "参数错误"),
    /** 未登录或凭证失效 */
    UNAUTHORIZED(401, "未授权"),
    /** 已登录但无权限（如非 ADMIN） */
    FORBIDDEN(403, "无权限"),
    /** 请求的资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 未知系统异常的兜底 */
    SYSTEM_ERROR(500, "系统异常");

    /** 错误码，对外接口返回体里的 code */
    private final Integer code;
    /** 默认错误提示文案 */
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

}
