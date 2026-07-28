package com.hape.photogallery.config;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void issueShare_shouldContainPhotoIds() {
        String token = jwtService.issueShare(List.of(1L, 2L, 3L), "view", 86400000);
        assertThat(token).isNotBlank();

        Claims claims = jwtService.verify(token);
        assertThat(claims).isNotNull();
        assertThat(claims.get("role")).isEqualTo("viewer");
        @SuppressWarnings("unchecked")
        List<Integer> photos = claims.get("photos", List.class);
        assertThat(photos).hasSize(3);
    }

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
}
