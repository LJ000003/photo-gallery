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

    // HS256 密钥，生产环境应从环境变量注入
    private static final String SECRET = System.getenv().getOrDefault(
            "JWT_SECRET", "demo1-photo-manager-jwt-secret-key-2024-min-256-bits!!");

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
