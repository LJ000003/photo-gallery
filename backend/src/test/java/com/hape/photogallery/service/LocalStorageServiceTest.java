package com.hape.photogallery.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 写路径穿越防线测试：store() 必须拒绝逃逸上传目录的目标路径，
 * 与读路径 resolveSafe 的约束对称。
 */
class LocalStorageServiceTest {

    @TempDir Path tempDir;

    private LocalStorageService service;

    @BeforeEach
    void setUp() {
        service = new LocalStorageService(tempDir.toString());
    }

    @Test
    void store_escapingTarget_shouldThrowSecurityException() {
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[]{1, 2, 3});
        Path escape = tempDir.resolve("../../evil.jpg").normalize();

        assertThatThrownBy(() -> service.store(file, escape))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid file path");
        assertThat(Files.exists(escape)).isFalse();
    }

    @Test
    void store_normalTarget_shouldWriteFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[]{1, 2, 3});
        Path target = tempDir.resolve("2026/08/uuid_x.jpg");
        Files.createDirectories(target.getParent());

        service.store(file, target);

        assertThat(Files.readAllBytes(target)).containsExactly(1, 2, 3);
    }

    @Test
    void resolveSafe_escapingPath_shouldThrowSecurityException() {
        assertThatThrownBy(() -> service.resolveSafe("../../evil.jpg"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void resolveSafe_normalPath_shouldReturnUnderUploadDir() {
        assertThat(service.resolveSafe("2026/08/x.jpg").startsWith(tempDir)).isTrue();
    }
}
