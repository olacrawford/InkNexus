package com.inknexus.book.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;

/**
 * 图书管理接口的角色校验拦截器。
 *
 * <p>图书查询是公开接口，只有增删改（POST/PUT/DELETE）需要 ADMIN 角色；
 * 角色来自网关解析 JWT 后注入的 X-User-Role 请求头，下游不信任客户端自带的同名请求头。
 * 旧签发的 token 不携带角色时网关统一按 USER 透传，写接口默认拒绝（fail-closed）。
 */
public class AdminOnlyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 查询类请求直接放行，与网关的公开策略保持一致
        if ("GET".equals(request.getMethod())) {
            return true;
        }

        if ("ADMIN".equals(request.getHeader("X-User-Role"))) {
            return true;
        }

        // 拦截器不走 @RestControllerAdvice，按网关 401 响应的同样格式手写 JSON
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.write("{\"code\":403,\"message\":\"无权限执行该操作\",\"data\":null}");
        }
        return false;
    }
}
