package com.iflytek.interview.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类：生成 / 解析 / 验证 Token
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    // 由字符串密钥构造 SecretKey（HS256）
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Token（第9讲起：只放 userId + sessionId，角色/权限改存 Redis 会话）
     *
     * @param userId    用户ID
     * @param sessionId 会话 key（登录时写入 Redis，JWT 里只做引用）
     */
    public String generateToken(Long userId, String sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("sessionId", sessionId);   // 会话 key，不再内嵌角色/权限

        Date now = new Date();
        return Jwts.builder()
                .claims(claims)                                    // 业务数据（claim）
                .subject(String.valueOf(userId))                   // 主体（放用户ID）
                .issuedAt(now)                                     // 签发时间
                .expiration(new Date(now.getTime() + expiration))  // 过期时间
                .signWith(getKey())                                // 签名（HS256 + 密钥）
                .compact();
    }

    /**
     * 解析 Token，拿到业务数据（Claims）
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())      // 校验签名用的密钥
                .build()
                .parseSignedClaims(token)  // 解析并校验证签名
                .getPayload();
    }

    /**
     * 验证 Token 是否有效（签名正确且未过期）
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}