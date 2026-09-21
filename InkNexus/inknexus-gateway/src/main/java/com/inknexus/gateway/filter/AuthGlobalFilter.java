package com.inknexus.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关全局鉴权过滤器：请求转发到下游前统一校验 JWT，把解析出的用户身份
 * 写入 X-User-Id / X-User-Role 透传给下游服务。
 * 信任边界：下游不解析 JWT，只认网关注入的这两个头（管理接口的 ADMIN 判权依赖
 * token 的 role claim），因此必须覆盖式重写，客户端伪造的同名头不能透传；
 * 白名单（isPublic）之外一律 fail-closed：无 token、格式不对或解析失败都返回 401。
 * 另对仅供服务间 Feign 直连的内部端点做外部封禁（INTERNAL_ONLY_PATHS）。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 内部端点：调用方（订单服务预占库存）走 Nacos 服务发现直连 stock，
     * 不经过网关；这里先于鉴权拦截，合法 token 也不放行，防止绕过订单状态篡改库存。
     */
    private static final List<String> INTERNAL_ONLY_PATHS = List.of("/api/stock/deduct");

    private final SecretKey secretKey;

    public AuthGlobalFilter(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 过滤器核心逻辑：每个经过网关的请求在此完成鉴权与身份透传。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (isInternalOnly(exchange.getRequest().getURI().getPath())) {
            return forbidden(exchange);
        }

        if (isPublic(exchange)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "缺少登录凭证");
        }

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(authHeader.substring(7))
                    .getBody();

            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            //覆盖式写入身份头：header() 是 put 语义（整体覆盖），客户端伪造的 X-User-Id / X-User-Role 都会被替换；
            //旧签发的 token 不带 role claim，统一按 USER 透传，管理接口对存量 token 默认关闭（fail-closed）
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role == null ? "USER" : role)
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            return unauthorized(exchange, "登录凭证无效或已过期");
        }
    }

    private boolean isInternalOnly(String path) {
        return INTERNAL_ONLY_PATHS.contains(path);
    }

    private boolean isPublic(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        // 登录、注册、健康检查公开
        if (path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/register")
                || path.endsWith("/hello")) {
            return true;
        }

        // 图书浏览和库存查询（GET）公开，增删改与下单预占仍需登录
        return HttpMethod.GET.equals(method)
                && (path.startsWith("/api/books") || path.startsWith("/api/stock"));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8)))
        );
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        String body = "{\"code\":403,\"message\":\"内部接口，禁止外部访问\",\"data\":null}";
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8)))
        );
    }

    /**
     * 数值越小越先执行，-100 保证鉴权过滤器优先执行。
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
