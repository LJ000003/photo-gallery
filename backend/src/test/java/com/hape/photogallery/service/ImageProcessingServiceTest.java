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
        // maxDecodeDim 传小值（50）便于测试解码上限；线上默认 4096
        return new ImageProcessingService(mock(ExifService.class), tempDir.toString(), 50);
    }

    // ==================== getFormat（按实际字节魔数判断，P2 修复） ====================

    @Test
    void getFormat_jpegMagic_shouldReturnJpeg() throws IOException {
        ImageProcessingService svc = newService();
        // 扩展名与字节分离验证：JPEG 魔数无论文件名如何都识别为 JPEG
        assertThat(svc.getFormat(writeMagic("a.jpg", 0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo("JPEG");
        assertThat(svc.getFormat(writeMagic("a.png", 0xFF, 0xD8, 0xFF, 0xE0)))
                .as("PNG 名 + JPEG 字节（旋转写回后的实际状态）必须按字节判为 JPEG")
                .isEqualTo("JPEG");
    }

    @Test
    void getFormat_pngGifBmpMagic_shouldMatchBytes() throws IOException {
        ImageProcessingService svc = newService();
        assertThat(svc.getFormat(writeMagic("a.png", 0x89, 0x50, 0x4E, 0x47))).isEqualTo("PNG");
        assertThat(svc.getFormat(writeMagic("a.gif", 0x47, 0x49, 0x46, 0x38))).isEqualTo("GIF");
        assertThat(svc.getFormat(writeMagic("a.bmp", 0x42, 0x4D))).isEqualTo("BMP");
    }

    @Test
    void getFormat_webpMagic_shouldReturnWebpNotJpeg() throws IOException {
        // 回归：曾返回 "JPEG" 导致 JPEG 字节写进 .webp 文件名并丢 alpha
        ImageProcessingService svc = newService();
        assertThat(svc.getFormat(writeMagic("a.webp",
                0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50))).isEqualTo("WebP");
    }

    @Test
    void getFormat_unknownMagic_shouldDefaultToJpeg() throws IOException {
        ImageProcessingService svc = newService();
        assertThat(svc.getFormat(writeFile("a.txt"))).isEqualTo("JPEG");
        assertThat(svc.getFormat(writeFile("noext"))).isEqualTo("JPEG");
    }

    // ==================== decodeCapped（降采样解码上限，2C4G 部署 P0） ====================

    /** 写一张真实 JPEG 到临时目录，返回路径 */
    private Path writeJpegImage(String name, int w, int h) throws IOException {
        Path p = tempDir.resolve(name);
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(img, "jpeg", p.toFile());
        return p;
    }

    @Test
    void decodeCapped_largeImage_shouldDownsamplePreservingAspectRatio() throws IOException {
        ImageProcessingService svc = newService(); // maxDecodeDim=50
        Path big = writeJpegImage("big.jpg", 200, 100);

        java.awt.image.BufferedImage decoded = svc.decodeCapped(big);

        // 长边 ≤50 且长宽比保持 2:1（单系数两轴同用——各轴独立取整会失真为 1.2:1）
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isLessThanOrEqualTo(50);
        assertThat((double) decoded.getWidth() / decoded.getHeight()).isCloseTo(2.0,
                org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    void decodeCapped_smallImage_shouldReturnAsIs() throws IOException {
        ImageProcessingService svc = newService();
        Path small = writeJpegImage("small.jpg", 40, 20);

        java.awt.image.BufferedImage decoded = svc.decodeCapped(small);

        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(40);
        assertThat(decoded.getHeight()).isEqualTo(20);
    }

    @Test
    void decodeCapped_corruptedFile_shouldReturnNull() throws IOException {
        ImageProcessingService svc = newService();
        Path broken = tempDir.resolve("broken.jpg");
        Files.write(broken, new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00, 0x01, 0x02});

        assertThat(svc.decodeCapped(broken)).isNull();
    }

    @Test
    void decodeCapped_missingFile_shouldReturnNull() {
        ImageProcessingService svc = newService();
        assertThat(svc.decodeCapped(tempDir.resolve("nope.jpg"))).isNull();
    }

    // ==================== 缩略图/WebP 原子写（P0：非原子直写会留下损坏产物） ====================

    @Test
    void generateThumbnail_shouldLeaveNoTmpResidue() throws IOException {
        ImageProcessingService svc = newService();
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(100, 50, java.awt.image.BufferedImage.TYPE_INT_RGB);

        svc.generateThumbnail(img, "2024/01", "base.jpg", 400);

        try (var stream = Files.list(tempDir.resolve("2024/01/thumbnails"))) {
            assertThat(stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".tmp"))).isEmpty();
        }
        assertThat(Files.exists(tempDir.resolve("2024/01/thumbnails/base.jpg"))).isTrue();
    }

    @Test
    void generateWebp_shouldLeaveNoTmpResidue() throws IOException {
        ImageProcessingService svc = newService();
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB);

        // 有/无 webp 编码器都不得残留 tmp（编码器缺失时 tmp 从未创建；存在时 finally 兜底清理）
        svc.generateWebp(img, "2024/01", "base");

        Path webpDir = tempDir.resolve("2024/01/webp");
        if (Files.exists(webpDir)) {
            try (var stream = Files.list(webpDir)) {
                assertThat(stream.map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".tmp"))).isEmpty();
            }
        }
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

    // ==================== writeOriginalJpeg（原子写，P1 修复） ====================

    @Test
    void writeOriginalJpeg_shouldReplaceAtomicallyWithoutTmpResidue() throws IOException {
        ImageProcessingService svc = newService();
        Path target = tempDir.resolve("out.jpg");
        Files.writeString(target, "old content");

        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB);
        svc.writeOriginalJpeg(img, target);

        // 原文件已被 JPEG 覆盖（不再是旧内容），临时文件不残留
        byte[] bytes = Files.readAllBytes(target);
        assertThat(bytes[0]).isEqualTo((byte) 0xFF);
        assertThat(bytes[1]).isEqualTo((byte) 0xD8);
        assertThat(Files.exists(tempDir.resolve("out.jpg.tmp"))).isFalse();
    }

    @Test
    void writeOriginalJpeg_missingTarget_shouldCreateIt() throws IOException {
        ImageProcessingService svc = newService();
        Path target = tempDir.resolve("new.jpg");

        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_RGB);
        svc.writeOriginalJpeg(img, target);

        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.exists(tempDir.resolve("new.jpg.tmp"))).isFalse();
    }

    private Path writeFile(String name) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, "x");
        return p;
    }

    /** 写指定魔数字节（int 便于以 0x 字面量书写） */
    private Path writeMagic(String name, int... bytes) throws IOException {
        Path p = tempDir.resolve(name);
        byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = (byte) bytes[i];
        }
        Files.write(p, data);
        return p;
    }
}
