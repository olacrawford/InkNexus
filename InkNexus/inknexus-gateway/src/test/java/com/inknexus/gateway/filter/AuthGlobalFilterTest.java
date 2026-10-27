package com.inknexus.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthGlobalFilter} 的鉴权与身份透传单元测试：纯 Mockito，不启动网关容器。
 */
@ExtendWith(MockitoExtension.class)
class AuthGlobalFilterTest {

    private static final String SECRET = "inknexus-gateway-secret-key-20260827-0123456789";

    @Mock
    private GatewayFilterChain chain;

    private AuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthGlobalFilter(SECRET);
    }

    @Test
    void filter_passesUserIdHeaderDownstream_whenTokenIsValid() {
        String token = token("7");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertEquals("7", captor.getValue().getRequest().getHeaders().getFirst("X-User-Id"));
    }

    @Test
    void filter_returns401_whenTokenMissing() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_returns401_whenTokenInvalid() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_passesPublicBookRequest_withoutToken() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/books").build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void filter_stripsForgedIdentityHeaders_onPublicPath() {
        // 公开路径不鉴权，但客户端伪造的身份头同样不能透传给下游
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/books")
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN")
                        .build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders downstream = captor.getValue().getRequest().getHeaders();
        assertNull(downstream.getFirst("X-User-Id"));
        assertNull(downstream.getFirst("X-User-Role"));
    }

    @Test
    void filter_overridesForgedUserIdHeader_whenClientSuppliesOne() {
        // 防回归：mutate().header() 的覆盖语义是把伪造头整体替换，一旦框架改为追加语义此用例必须失败
        String token = token("7");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Id", "999")
                        .build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders downstream = captor.getValue().getRequest().getHeaders();
        assertEquals("7", downstream.getFirst("X-User-Id"));
        assertEquals(1, downstream.get("X-User-Id").size());
    }

    @Test
    void filter_passesRoleHeaderDownstream_whenTokenCarriesRole() {
        String token = token("7", "ADMIN");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertEquals("ADMIN", captor.getValue().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void filter_overridesForgedRoleHeader_whenTokenHasNoRole() {
        // 旧 token 没有 role claim 时必须按 USER 透传，客户端伪造的 X-User-Role 不能放行管理接口
        String token = token("7");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Role", "ADMIN")
                        .build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertEquals("USER", captor.getValue().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void filter_returns403ForInternalDeduct_whenValidAdminToken() {
        // 防回归：/stock/deduct 仅供订单服务 Feign 直连，网关侧必须对外封禁，合法 ADMIN token 也不放行
        String token = token("7", "ADMIN");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/stock/deduct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_returns403ForInternalDeduct_whenTokenMissing() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/stock/deduct").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_passesPublicStockQuery_withoutToken() {
        // 内部端点封禁不能扩大化：库存只读查询仍属公开白名单
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/stock/1").build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    private String token(String subject) {
        return token(subject, null);
    }

    private String token(String subject, String role) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .setSubject(subject)
                .claim("username", "tester");
        if (role != null) {
            builder.claim("role", role);
        }
        return builder
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
