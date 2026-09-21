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
 *
 * <p>存在意义（纵深防御）：网关是第一层判权，本拦截器是服务内第二层——
 * 当请求绕过网关直连 8070 端口时，网关注入的头不可信，这里仍按「头里是否为 ADMIN」兜底，
 * 因此前提是服务端口只在内网暴露；能直连端口的调用者本就可伪造该头，边界必须收在网关。
 */
public class AdminOnlyInterceptor implements HandlerInterceptor {

    /** 非 GET 要求 X-User-Role 恰为 ADMIN；返回 false 时已向客户端写出 403 JSON，请求终止 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
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
