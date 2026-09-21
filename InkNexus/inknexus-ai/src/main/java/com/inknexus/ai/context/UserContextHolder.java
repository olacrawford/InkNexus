package com.inknexus.ai.context;

/**
 * 用户上下文持有器：用 ThreadLocal 暂存“当前请求的用户 ID”，供本请求任意位置读取，避免层层传参。
 * <p>值只能来自网关注入的 X-User-Id（经 JWT 校验后写入），本服务绝不接受其他来源；
 * ThreadLocal 只对写入它的那个请求线程可见，写入/清理由 UserContextInterceptor 在请求进出时配对完成。
 */
public class UserContextHolder {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    /** 请求结束时清空，防止线程池复用导致“串号”或内存泄漏。 */
    public static void clear() {
        USER_ID.remove();
    }
}