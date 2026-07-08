package com.hape.photogallery.config;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    @Test
    void issueAdmin_shouldReturnValidToken() {
        String token = JwtUtil.issueAdmin(3600000);
        assertThat(token).isNotBlank();

        Claims claims = JwtUtil.verify(token);
        assertThat(claims).isNotNull();
        assertThat(claims.get("role")).isEqualTo("admin");
    }

    @Test
    void issueShare_shouldContainPhotoIds() {
        String token = JwtUtil.issueShare(List.of(1L, 2L, 3L), "view", 86400000);
        assertThat(token).isNotBlank();

        Claims claims = JwtUtil.verify(token);
        assertThat(claims).isNotNull();
        assertThat(claims.get("role")).isEqualTo("viewer");
        @SuppressWarnings("unchecked")
        List<Integer> photos = claims.get("photos", List.class);
        assertThat(photos).hasSize(3);
    }

    @Test
    void verify_invalidToken_shouldReturnNull() {
        assertThat(JwtUtil.verify("invalid.token.here")).isNull();
    }

    @Test
    void verify_null_shouldThrow() {
        assertThatThrownBy(() -> JwtUtil.verify(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verify_emptyString_shouldThrow() {
        assertThatThrownBy(() -> JwtUtil.verify(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
