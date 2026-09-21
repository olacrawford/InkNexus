package com.inknexus.ai.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求拦截器：在请求进/出时，把网关注入的 X-User-Id 写入并清空用户上下文。
 * <p>本服务不校验 JWT（由网关统一负责），只信任网关写下的 X-User-Id；
 * 后续 Feign 调用查订单都基于这里恢复的用户身份，是防止模型跨用户查单的第一环。
 */
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uid = request.getHeader("X-User-Id");
        if (uid != null) {
            UserContextHolder.setUserId(Long.valueOf(uid));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束必须清理，否则线程被复用时可能残留上一个用户的 ID
        UserContextHolder.clear();
    }
}