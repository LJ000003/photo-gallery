package com.hape.photogallery.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProcessingMessageTest {

    @Test
    void constructorAndGetters_shouldWork() {
        ProcessingMessage msg = new ProcessingMessage(1L, "2024/08/test.jpg", "2024/08", "test.jpg", "my-watermark");

        assertThat(msg.getPhotoId()).isEqualTo(1L);
        assertThat(msg.getTargetPath()).isEqualTo("2024/08/test.jpg");
        assertThat(msg.getDateDir()).isEqualTo("2024/08");
        assertThat(msg.getBaseName()).isEqualTo("test.jpg");
        assertThat(msg.getWatermark()).isEqualTo("my-watermark");
    }

    @Test
    void setters_shouldWork() {
        ProcessingMessage msg = new ProcessingMessage();
        msg.setPhotoId(2L);
        msg.setTargetPath("2024/09/other.jpg");
        msg.setDateDir("2024/09");
        msg.setBaseName("other.jpg");
        msg.setWatermark(null);

        assertThat(msg.getPhotoId()).isEqualTo(2L);
        assertThat(msg.getWatermark()).isNull();
    }
}
