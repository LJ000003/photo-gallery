package com.hape.photogallery.config;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${JWT_SECRET:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "必须设置环境变量 JWT_SECRET，例如: export JWT_SECRET=$(openssl rand -base64 32)");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥 ≥32 字节，过短仅靠 Keys.hmacShaKeyFor 运行时兜底，启动即暴露更清晰
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "JWT_SECRET 过短（当前 " + keyBytes.length + " 字节），HS256 要求至少 32 字节，"
                    + "请用 openssl rand -base64 32 生成后重新设置");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String issueAdmin(long durationMs) {
        return Jwts.builder()
                .claim("role", "admin")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + durationMs))
                .signWith(key)
                .compact();
    }
    // P0-#6：issueShare 已删除——分享凭证改为 DB 高熵 token（可撤销），JWT 退出分享签发；
    // JwtAuthFilter 保留 legacy viewer JWT 校验分支过渡（旧链接最长 7 天自然失效）

    public Claims verify(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException e) {
            return null;
        }
    }
}
