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

/**
 * 网关全局鉴权过滤器
 * 请求转发到下游微服务之前，统一校验JWT令牌
 * 解析出userId，放入请求头X-User-Id透传给下游服务，下游直接拿请求头获取登录用户id
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {
// 实现两个接口：
// 1.GlobalFilter：全局过滤器，所有请求都会进入filter()方法
// 2.Ordered：设置过滤器执行顺序

    //JWT签名校验密钥对象
    private final SecretKey secretKey;

    /**
     * 构造方法读取yml的jwt.secret字符串，转为加密密钥对象
     * @param secret yml配置的jwt密钥字符串
     */
    public AuthGlobalFilter(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 过滤器核心逻辑：每一个经过网关的请求都会进入该方法
     * @param exchange 请求上下文，封装request、response
     * @param chain 过滤器链，放行请求
     * @return Mono<Void> 响应流
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //公开接口（登录/注册/hello/图书浏览）直接放行
        if (isPublic(exchange)) {
            return chain.filter(exchange);
        }

        //获取请求头Authorization，拿到前端传过来的token
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        //没有携带token，或者token格式不是Bearer xxx，返回401未授权
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "缺少登录凭证");
        }

        try {
            //截取掉Bearer前缀，解析token，校验签名、校验过期时间
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(authHeader.substring(7))
                    .getBody();

            //从token载荷取出userId与角色
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            //修改请求，覆盖式写入身份与角色请求头，把用户透传给下游服务。
            //header() 是 put 语义（整体覆盖），客户端伪造的 X-User-Id / X-User-Role 都会被这里替换；
            //旧签发的 token 不带 role claim，统一按 USER 透传，管理接口对存量 token 默认关闭（fail-closed）
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role == null ? "USER" : role)
                    .build();

            //把修改后的请求往下游服务转发
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            //token过期、篡改、格式错误，捕获异常返回401
            return unauthorized(exchange, "登录凭证无效或已过期");
        }
    }

    /**
     * 判断接口是否公开无需鉴权
     * @param exchange 请求上下文
     * @return true：公开接口放行；false：需要校验token
     */
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

    /**
     * 返回401未授权的JSON响应
     * @param exchange 请求上下文
     * @param message 返回提示信息
     * @return Mono<Void>
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        //设置响应状态码401
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        //设置响应类型为json
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        //组装返回的json字符串
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8)))
        );
    }

    /**
     * 设置过滤器执行优先级，数值越小越先执行，-100保证鉴权过滤器优先执行
     * @return 优先级数值
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
