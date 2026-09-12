package com.inknexus.auth.util;

import com.inknexus.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

//生成Token和解析Token
public class JwtUtil {

    //密钥和过期时间（24小时）
    private final SecretKey secretKey;
    private final long expireSeconds;

    // 构造方法，new JwtUtil(secret,expireSeconds)的时候执行
    public JwtUtil(String secret, long expireSeconds) {
        //密码学算法最终处理的是字节
        //把yml配置里的普通字符串密钥（secret），转换成JWT签名所需要的SecretKey加密密钥对象
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    //生成Token
    public String generateToken(User user) {
        //获取当前时间
        Date now = new Date();
        //获取过期时间
        Date expireAt = new Date(now.getTime() + expireSeconds * 1000);

        //JWT=头部：算法；载荷：存你的 userId、username，签发时间；签名。
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))   // subject主体，存放用户id
                .claim("username", user.getUsername())    //自定义载荷字段，存入用户名
                .claim("nickname", user.getNickname())    //自定义载荷字段，存入昵称
                .setIssuedAt(now)                           //设置token签发时间：什么时候生成的
                .setExpiration(expireAt)                    //设置token过期时间
                .signWith(secretKey)                        //使用密钥对象对整个JWT做签名，防止篡改
                .compact();                                 //把头部、载荷、签名拼接成一整个token字符串返回
    }

    //解析 JWT
    //Claims 可以理解成：JWT 里面存放的数据集合
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()         //构建解析器
                .setSigningKey(secretKey)   //传入和生成token一模一样的密钥，用来校验签名
                .build()                    //构建解析实例
                .parseClaimsJws(token)      //解析token，校验签名、校验过期；出错抛异常
                .getBody();                 //拿到payload载荷，返回Claims对象
    }

}