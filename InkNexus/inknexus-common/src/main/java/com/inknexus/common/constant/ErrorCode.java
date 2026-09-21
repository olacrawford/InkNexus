package com.inknexus.common.constant;

import lombok.Getter;

/**
 * 全系统统一业务错误码枚举：code 风格与 HTTP 状态码对齐，供 {@link com.inknexus.common.exception.BusinessException}
 * 携带、最终落到 {@link com.inknexus.common.result.Result#getCode()} 返回给前端或 Feign 调用方。
 * 新增错误码直接在这里扩展，保持各服务 code 语义一致。
 */
@Getter
public enum ErrorCode {
    /** 成功码，与 Result#success 的 200 同一约定 */
    SUCCESS(200, "success"),
    /** 请求参数不合法（缺失、格式、类型等） */
    PARAM_ERROR(400, "参数错误"),
    /** 未登录或登录凭证无效/过期 */
    UNAUTHORIZED(401, "未授权"),
    /** 已登录但权限不足（如非 ADMIN 访问管理接口） */
    FORBIDDEN(403, "无权限"),
    /** 请求的资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 未预期的系统内部异常 */
    SYSTEM_ERROR(500, "系统异常");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

}