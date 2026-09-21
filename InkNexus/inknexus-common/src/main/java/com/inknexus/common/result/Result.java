package com.inknexus.common.result;

import lombok.Data;

/**
 * 统一接口契约：所有业务服务的 HTTP 响应与 Feign 跨服务调用返回值都用本类包装，
 * 调用方只认 code + message + data 三段结构，code==200 视为成功。
 *
 * <p>成功由 {@link #success(Object)} 固定返回 code=200；失败走 {@link #fail}，
 * code 通常取 {@link com.inknexus.common.constant.ErrorCode} 或业务自定义值（如 409 库存不足），
 * 兜底转换逻辑见 GlobalExceptionHandler。改动本结构会同时影响前端解包与所有 Feign 解码。</p>
 */
@Data
public class Result<T> {
    /** 业务状态码：200 成功；400/401/403/404/500 语义与 HTTP 对齐，业务可扩展 */
    private Integer code;
    /** 提示信息：成功固定 "success"，失败为可直接展示的文案 */
    private String message;
    /** 业务数据载荷：失败时为 null */
    private T data;

    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功响应：code 固定 200，data 可为 null。 */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /** 失败响应：指定业务错误码与提示，data 为 null。 */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败响应便捷重载：code 固定 500。 */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }
}