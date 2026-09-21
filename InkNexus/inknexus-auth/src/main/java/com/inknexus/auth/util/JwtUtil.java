package com.inknexus.auth.util;

import com.inknexus.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：生成Token和解析Token。
 * <p>采用 HS256 对称签名，密钥来自 Nacos 配置 {@code jwt.secret}，auth 与 gateway 共用同一密钥，
 * 网关才能验签并把用户身份转换为 {@code X-User-Id}/{@code X-User-Role} 传给下游。
 * HS256 要求密钥长度不小于 32 字节，过短时 Keys.hmacShaKeyFor 会直接抛异常。
 */
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expireSeconds;

    public JwtUtil(String secret, long expireSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    //token 一旦签发在过期前无法主动作废（无服务端黑名单），泄露风险由短过期时间缓解
    public String generateToken(User user) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireSeconds * 1000);

        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("nickname", user.getNickname())
                .claim("role", user.getRole() == null ? "USER" : user.getRole())  // role 供网关透传给下游做管理接口鉴权
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