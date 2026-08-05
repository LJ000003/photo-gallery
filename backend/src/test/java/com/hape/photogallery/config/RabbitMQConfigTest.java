package com.hape.photogallery.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RabbitMQConfigTest {

    @Test
    void constants_shouldBeCorrect() {
        assertThat(RabbitMQConfig.EXCHANGE).isEqualTo("pg.photo.ex");
        assertThat(RabbitMQConfig.DLX_EXCHANGE).isEqualTo("pg.photo.dlx");
        assertThat(RabbitMQConfig.QUEUE).isEqualTo("pg.photo.processing");
        assertThat(RabbitMQConfig.DLQ).isEqualTo("pg.photo.processing.dlq");
        assertThat(RabbitMQConfig.ROUTING_KEY).isEqualTo("photo.processing");
        assertThat(RabbitMQConfig.DLQ_ROUTING_KEY).isEqualTo("photo.processing.dlq");
        assertThat(RabbitMQConfig.RETRY_QUEUE).isEqualTo("pg.photo.processing.retry");
        assertThat(RabbitMQConfig.RETRY_ROUTING_KEY).isEqualTo("photo.processing.retry");
        assertThat(RabbitMQConfig.RETRY_TTL_MS).isEqualTo(10_000L);
    }
}
