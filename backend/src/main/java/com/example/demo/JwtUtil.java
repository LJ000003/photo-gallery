package com.example.demo;

import java.security.Key;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

public class JwtUtil {

    private static final String SECRET = requireEnv("JWT_SECRET");

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "必须设置环境变量 " + name + "，例如: export " + name + "=$(openssl rand -base64 32)"
            );
        }
        return value;
    }

    private static final Key KEY = Keys.hmacShaKeyFor(SECRET.getBytes());

    /** 签发 admin JWT */
    public static String issueAdmin(long durationMs) {
        return Jwts.builder()
                .claim("role", "admin")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + durationMs))
                .signWith(KEY)
                .compact();
    }

    /** 签发分享链接 JWT */
    public static String issueShare(List<Long> photoIds, String permission, long durationMs) {
        return Jwts.builder()
                .claim("role", "viewer")
                .claim("photos", photoIds)
                .claim("permission", permission)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + durationMs))
                .signWith(KEY)
                .compact();
    }

    /** 验签并返回 Claims，无效则返回 null */
    public static Claims verify(String token) {
        try {
            return Jwts.parser().verifyWith((SecretKey) KEY).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException e) {
            return null;
        }
    }
}
