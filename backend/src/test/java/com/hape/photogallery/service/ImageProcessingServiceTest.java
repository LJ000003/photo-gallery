package com.hape.photogallery.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ImageProcessingServiceTest {

    @TempDir Path tempDir;

    private ImageProcessingService newService() {
        return new ImageProcessingService(mock(ExifService.class), tempDir.toString());
    }

    // ==================== getFormat ====================

    @Test
    void getFormat_jpeg_shouldReturnJpeg() throws IOException {
        ImageProcessingService svc = newService();
        assertThat(svc.getFormat(writeFile("a.jpg"))).isEqualTo("JPEG");
        assertThat(svc.getFormat(writeFile("a.JPEG"))).isEqualTo("JPEG");
    }

    @Test
    void getFormat_pngGifBmp_shouldMatchExtension() throws IOException {
        ImageProcessingService svc = newService();
        assertThat(svc.getFormat(writeFile("a.png"))).isEqualTo("PNG");
        assertThat(svc.getFormat(writeFile("a.gif"))).isEqualTo("GIF");
        assertThat(svc.getFormat(writeFile("a.bmp"))).isEqualTo("BMP");
    }

    @Test
    void getFormat_webp_shouldReturnWebpNotJpeg() throws IOException {
        // 回归：曾返回 "JPEG" 导致 JPEG 字节写进 .webp 文件名并丢 alpha
        ImageProcessingService svc = newService();
        assertThat(svc.getFormat(writeFile("a.webp"))).isEqualTo("WebP");
        assertThat(svc.getFormat(writeFile("a.webp"))).isNotEqualTo("JPEG");
    }

    @Test
    void getFormat_unknownOrMissingName_shouldDefaultToJpeg() throws IOException {
        ImageProcessingService svc = newService();
        assertThat(svc.getFormat(writeFile("a.txt"))).isEqualTo("JPEG");
        assertThat(svc.getFormat(writeFile("noext"))).isEqualTo("JPEG");
    }

    // ==================== magic bytes（顺带覆盖既有行为） ====================

    @Test
    void validateImageMagicBytes_acceptsKnownFormats() throws IOException {
        ImageProcessingService svc = newService();
        svc.validateImageMagicBytes(new java.io.ByteArrayInputStream(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})); // JPEG
        svc.validateImageMagicBytes(new java.io.ByteArrayInputStream(
                new byte[]{0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50})); // WEBP
    }

    @Test
    void validateImageMagicBytes_rejectsGarbage() {
        ImageProcessingService svc = newService();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        svc.validateImageMagicBytes(new java.io.ByteArrayInputStream(new byte[]{1, 2, 3})))
                .isInstanceOf(com.hape.photogallery.exception.InvalidFileTypeException.class);
    }

    private Path writeFile(String name) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, "x");
        return p;
    }
}
