package com.inknexus.common.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户上下文拦截器：请求进入时从网关注入的 {@code X-User-Id}/{@code X-User-Role} 恢复身份，
 * 请求结束时（无论正常还是异常，afterCompletion 必然回调）清理上下文。
 * <p>本服务不校验 JWT（由网关统一负责），只信任网关注入的头；服务间 Feign 直连不带网关，
 * 但调用方注入的头同样被视为可信内部身份，与网关路径语义一致。
 * <p>preHandle 先清空再按头写入：公开/无头请求保持空上下文，同时兜底清理任何线程复用残留；
 * 头的值不是合法数字时按无身份处理（业务侧经 requireUserId 得到 401），不让格式错误变成 500。
 */
@Slf4j
public class CurrentUserInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
        String uid = request.getHeader(UserContext.USER_ID_HEADER);
        if (uid != null) {
            try {
                UserContext.setUserId(Long.valueOf(uid));
                UserContext.setRole(request.getHeader(UserContext.USER_ROLE_HEADER));
            } catch (NumberFormatException ex) {
                log.debug("非法的用户身份头，按无身份处理：{}", uid);
                UserContext.clear();
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

}
