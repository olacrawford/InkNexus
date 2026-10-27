package com.inknexus.common.context;

import com.inknexus.common.constant.ErrorCode;
import com.inknexus.common.exception.BusinessException;

/**
 * 当前用户上下文：用 ThreadLocal 暂存“本请求的用户 ID/角色”，业务代码经 {@code UserContext.requireUserId()}
 * 取身份，替代各控制器重复声明 {@code @RequestHeader("X-User-Id")}。
 * <p>值只能来自网关注入的身份头（经 JWT 校验后写入）或服务间 Feign 调用显式携带的头，
 * 由 {@link CurrentUserInterceptor} 在请求进入/结束时配对写入与清理；工具类等请求线程之外的场景
 * （如 AI 工具回调线程）允许自行 set/clear，但必须保证配对。
 * <p>ThreadLocal 只对写入它的线程可见，不跨线程传递；异步/线程池场景不能假设上下文仍在。
 */
public final class UserContext {

    /** 网关注入、下游消费的用户身份头名，Gateway 与 CurrentUserInterceptor 共用同一约定 */
    public static final String USER_ID_HEADER = "X-User-Id";
    /** 网关注入的用户角色头名，语义同上 */
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static void setRole(String role) {
        ROLE.set(role);
    }

    /** 当前用户 ID，无请求上下文（如定时任务、MQ 消费线程）时返回 null，调用方自行判空 */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /** 当前用户角色，未注入（如内部 Feign 调用只带 X-User-Id）时返回 null */
    public static String getRole() {
        return ROLE.get();
    }

    /**
     * 当前用户 ID，缺失时抛 401 业务异常（由 GlobalExceptionHandler 转成统一响应）。
     * 控制器取身份的标准入口：身份缺失即未授权，不进入业务逻辑。
     */
    public static Long requireUserId() {
        Long userId = USER_ID.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户身份缺失，请通过网关访问");
        }
        return userId;
    }

    /** 请求结束时清空，防止线程池复用导致“串号”或内存泄漏 */
    public static void clear() {
        USER_ID.remove();
        ROLE.remove();
    }

}
