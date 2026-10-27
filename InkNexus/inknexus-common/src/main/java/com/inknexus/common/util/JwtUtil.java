package com.inknexus.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：生成Token和解析Token，auth 与 gateway 共用同一实现。
 * <p>采用 HS256 对称签名，密钥来自 Nacos 配置 {@code jwt.secret}，签发方（auth）与
 * 验签方（gateway）共用同一密钥，网关才能验签并把用户身份转换为
 * {@code X-User-Id}/{@code X-User-Role} 传给下游。claims 的写入与读取都通过
 * {@link #CLAIM_ROLE}/{@link #DEFAULT_ROLE} 契约常量，两端不允许各自硬编码。
 * <p>HS256 要求密钥长度不小于 32 字节，过短时 Keys.hmacShaKeyFor 会直接抛异常。
 */
public class JwtUtil {

    /** 角色所在 claim 名，签发端写入、网关验签端读取，改任一侧必须同步 */
    public static final String CLAIM_ROLE = "role";

    /** 角色缺省值：签发端 role 为空时落此值；存量 token 不带 role claim 时网关也按此透传（fail-closed） */
    public static final String DEFAULT_ROLE = "USER";

    private final SecretKey secretKey;
    private final long expireSeconds;

    /** 签发方使用：expireSeconds 决定 token 有效期 */
    public JwtUtil(String secret, long expireSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    /** 仅验签方（网关）使用：只解析不签发，无有效期概念 */
    public JwtUtil(String secret) {
        this(secret, 0);
    }

    //token 一旦签发在过期前无法主动作废（无服务端黑名单），泄露风险由短过期时间缓解
    public String generateToken(Long userId, String username, String nickname, String role) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireSeconds * 1000);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .claim("nickname", nickname)
                .claim(CLAIM_ROLE, role == null ? DEFAULT_ROLE : role)  // role 供网关透传给下游做管理接口鉴权
                .setIssuedAt(now)
                .setExpiration(expireAt)
                .signWith(secretKey)
                .compact();
    }

    //验签失败或 token 已过期时抛 JJwt 运行时异常，调用方（网关）据此拒绝请求
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

}
