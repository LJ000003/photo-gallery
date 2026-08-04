package com.hape.photogallery.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProdSecurityValidatorTest {

    @Test
    void blankRedisPassword_shouldFailStartup() {
        assertThatThrownBy(() -> new ProdSecurityValidator("", "rabbit-secret", "hape"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIS_PASSWORD");
    }

    @Test
    void blankRabbitPassword_shouldFailStartup() {
        assertThatThrownBy(() -> new ProdSecurityValidator("redis-secret", "", "hape"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RABBIT_PASS");
    }

    @Test
    void defaultRabbitUser_shouldFailStartup() {
        assertThatThrownBy(() -> new ProdSecurityValidator("redis-secret", "rabbit-secret", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RABBIT_USER");
    }

    @Test
    void validCredentials_shouldPass() {
        assertThatCode(() -> new ProdSecurityValidator("redis-secret", "rabbit-secret", "hape"))
                .doesNotThrowAnyException();
    }
}
