package com.hape.photogallery.config;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-for-unit-tests-2026");
    }

    @Test
    void issueAdmin_shouldReturnValidToken() {
        String token = jwtService.issueAdmin(3600000);
        assertThat(token).isNotBlank();

        Claims claims = jwtService.verify(token);
        assertThat(claims).isNotNull();
        assertThat(claims.get("role")).isEqualTo("admin");
    }

    // issueShare 已删除——分享凭证改为 DB 高熵 token（可撤销），JWT 退出分享签发

    @Test
    void verify_invalidToken_shouldReturnNull() {
        assertThat(jwtService.verify("invalid.token.here")).isNull();
    }

    @Test
    void verify_null_shouldThrow() {
        assertThatThrownBy(() -> jwtService.verify(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verify_emptyString_shouldThrow() {
        assertThatThrownBy(() -> jwtService.verify(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_secretTooShort_shouldThrow() {
        // 30 字节 < 32 字节要求
        assertThatThrownBy(() -> new JwtService("short-secret-key-123456789"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少 32 字节");
    }

    @Test
    void constructor_blankSecret_shouldThrow() {
        assertThatThrownBy(() -> new JwtService("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
