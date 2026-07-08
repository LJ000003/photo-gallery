package com.hape.photogallery.service;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.repository.ExifDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExifServiceTest {

    @Mock private ExifDataRepository exifRepo;

    @TempDir Path tempDir;

    private ExifService service;

    @BeforeEach
    void setUp() {
        service = new ExifService(exifRepo);
    }

    // ==================== extractAndSave ====================

    @Test
    void extractAndSave_nullContentType_shouldReturnNull() {
        Photo p = new Photo(); p.setId(1L); p.setName("test");
        p.setContentType(null);
        p.setFileName("test.jpg");

        assertThat(service.extractAndSave(p, tempDir.resolve("test.jpg"))).isNull();
    }

    @Test
    void extractAndSave_nonImageType_shouldReturnNull() {
        Photo p = new Photo(); p.setId(1L); p.setName("test");
        p.setContentType("application/pdf");
        p.setFileName("test.pdf");

        assertThat(service.extractAndSave(p, tempDir.resolve("test.pdf"))).isNull();
    }

    @Test
    void extractAndSave_pngContentType_shouldReturnNull() {
        Photo p = new Photo(); p.setId(1L); p.setName("test");
        p.setContentType("image/png");
        p.setFileName("test.png");

        assertThat(service.extractAndSave(p, tempDir.resolve("test.png"))).isNull();
    }

    @Test
    void extractAndSave_jpegContentType_fileNotFound_shouldReturnNull() {
        Photo p = new Photo(); p.setId(1L); p.setName("test");
        p.setContentType("image/jpeg");
        p.setFileName("nonexistent.jpg");

        // 文件不存在，catch 块吞掉异常，无 EXIF 数据可存 → null
        assertThat(service.extractAndSave(p, tempDir.resolve("nonexistent.jpg"))).isNull();
        verify(exifRepo, never()).save(any());
    }

    @Test
    void extractAndSave_webpContentType_fileNotFound_shouldReturnNull() {
        Photo p = new Photo(); p.setId(1L); p.setName("test");
        p.setContentType("image/webp");
        p.setFileName("nonexistent.webp");

        assertThat(service.extractAndSave(p, tempDir.resolve("nonexistent.webp"))).isNull();
    }

    // ==================== extractForExisting ====================

    @Test
    void extractForExisting_emptyList_shouldReturnZero() {
        assertThat(service.extractForExisting(List.of(), tempDir)).isEqualTo(0);
    }

    @Test
    void extractForExisting_fileNotFound_shouldReturnZero() {
        Photo p = new Photo(); p.setId(1L); p.setName("test");
        p.setContentType("image/jpeg");
        p.setFileName("nonexistent.jpg");

        assertThat(service.extractForExisting(List.of(p), tempDir)).isEqualTo(0);
    }

    @Test
    void extractForExisting_nonImageType_shouldReturnZero() {
        Photo p = new Photo(); p.setId(1L); p.setName("test");
        p.setContentType("text/plain");
        p.setFileName("test.txt");

        assertThat(service.extractForExisting(List.of(p), tempDir)).isEqualTo(0);
    }

    @Test
    void extractForExisting_multiplePhotos_shouldCountSuccesses() throws Exception {
        Photo p1 = new Photo(); p1.setId(1L); p1.setName("a");
        p1.setContentType("image/jpeg"); p1.setFileName("a.jpg");
        Photo p2 = new Photo(); p2.setId(2L); p2.setName("b");
        p2.setContentType("image/png"); p2.setFileName("b.png"); // PNG 跳过
        Photo p3 = new Photo(); p3.setId(3L); p3.setName("c");
        p3.setContentType(null); p3.setFileName("c.jpg"); // null contentType 跳过

        // 文件存在但不含 EXIF（无法解析），extractAndSave 返回 null
        Files.write(tempDir.resolve("a.jpg"), new byte[]{(byte) 0xFF, (byte) 0xD8});

        assertThat(service.extractForExisting(List.of(p1, p2, p3), tempDir)).isEqualTo(0);
    }

    // ==================== getOrientation ====================

    @Test
    void getOrientation_fileNotFound_shouldReturn1() {
        assertThat(service.getOrientation(tempDir.resolve("no.jpg"))).isEqualTo(1);
    }

    @Test
    void getOrientation_nonImageFile_shouldReturn1() throws Exception {
        Path f = tempDir.resolve("text.txt");
        Files.write(f, "hello".getBytes());

        // 文本文件无法被 metadata-extractor 解析 → 返回默认值 1
        assertThat(service.getOrientation(f)).isEqualTo(1);
    }
}
