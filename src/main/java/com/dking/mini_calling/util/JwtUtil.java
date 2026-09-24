package com.dking.mini_calling.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationMs;

    /** 把字符串 secret 转成 SecretKey，HS256 要求至少 256 位（32 字节） */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token：subject 存 username，claims 里塞 userId 供后续使用
     * 0.12.x 新 API：subject() / issuedAt() / expiration() / signWith(SecretKey)
     */
    public String generateToken(String username, Long userId) {
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 token，失败会抛 JwtException 子类（过期/签名错/格式错）
     * 0.12.x 新 API：parser() / verifyWith() / parseSignedClaims() / getPayload()
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 校验通过返回 Claims，失败抛异常，让调用方决定怎么处理 */
    public Claims validateAndParse(String token) {
        return parseToken(token);
    }
}
