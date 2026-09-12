package com.inknexus.ai.context;

/** 用户上下文持有器：用 ThreadLocal 暂存“当前请求的用户 ID”，供本请求任意位置读取，避免层层传参。 */
public class UserContextHolder {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    /** 请求进入时写入（值来自网关注入的 X-User-Id）。 */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /** 读取当前用户 ID，供 Feign 拦截器 / 业务代码使用。 */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /** 请求结束时清空，防止线程池复用导致“串号”或内存泄漏。 */
    public static void clear() {
        USER_ID.remove();
    }
}