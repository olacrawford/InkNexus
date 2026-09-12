package com.inknexus.ai.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/** 请求拦截器：在请求进/出时，把网关注入的 X-User-Id 写入并清空用户上下文。 */
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 网关鉴权通过后会自动在请求头注入 X-User-Id，里面对应当前登录用户 ID
        String uid = request.getHeader("X-User-Id");
        if (uid != null) {
            UserContextHolder.setUserId(Long.valueOf(uid));
        }
        return true; // 返回 true 放行，继续走后续流程
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束必须清理，否则线程被复用时可能残留上一个用户的 ID
        UserContextHolder.clear();
    }
}