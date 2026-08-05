package com.hape.photogallery.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.repository.PhotoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * P0-#3：缩略图/WebP 缺失时必须返回 null（不回退原图），
 * 回退策略由调用方（PhotoController）按角色决定。
 */
@ExtendWith(MockitoExtension.class)
class FilePathResolverTest {

    private static final String FILE_NAME = "2024/01/uuid_test.jpg";

    @TempDir
    Path tempDir;

    @Mock
    private PhotoRepository repo;
    @Mock
    private StorageService storage;

    private FilePathResolver resolver;

    @BeforeEach
    void setUp() {
        // getUploadDir 仅缩略图路径使用（webp 路径不依赖），置 lenient 避免跨用例 UnnecessaryStubbing
        lenient().when(storage.getUploadDir()).thenReturn(tempDir);
        when(storage.resolveSafe(anyString()))
                .thenAnswer(inv -> tempDir.resolve(inv.getArgument(0, String.class)).normalize());
        resolver = new FilePathResolver(repo, storage);
    }

    private Photo photo() {
        Photo p = new Photo();
        p.setId(1L);
        p.setFileName(FILE_NAME);
        return p;
    }

    @Test
    void thumbnail_shouldReturnThumbPath_whenExists() throws Exception {
        when(repo.findById(1L)).thenReturn(Optional.of(photo()));
        Path thumb = tempDir.resolve("2024/01/thumbnails/uuid_test.jpg");
        Files.createDirectories(thumb.getParent());
        Files.createFile(thumb);

        assertThat(resolver.getThumbnailPath(1L, 400)).isEqualTo(thumb);
    }

    @Test
    void thumbnail_shouldReturnNull_whenMissing() {
        when(repo.findById(1L)).thenReturn(Optional.of(photo()));

        assertThat(resolver.getThumbnailPath(1L, 400)).isNull();
    }

    @Test
    void thumbnail_shouldFallbackTo400_whenCustomWidthMissing() throws Exception {
        when(repo.findById(1L)).thenReturn(Optional.of(photo()));
        Path thumb400 = tempDir.resolve("2024/01/thumbnails/uuid_test.jpg");
        Files.createDirectories(thumb400.getParent());
        Files.createFile(thumb400);

        assertThat(resolver.getThumbnailPath(1L, 200)).isEqualTo(thumb400);
    }

    @Test
    void thumbnail_shouldReturnNull_when400AlsoMissing() {
        when(repo.findById(1L)).thenReturn(Optional.of(photo()));

        assertThat(resolver.getThumbnailPath(1L, 200)).isNull();
    }

    @Test
    void webp_shouldReturnWebpPath_whenExists() throws Exception {
        when(repo.findById(1L)).thenReturn(Optional.of(photo()));
        Path webp = tempDir.resolve("2024/01/webp/uuid_test.jpg.webp");
        Files.createDirectories(webp.getParent());
        Files.createFile(webp);

        assertThat(resolver.getWebpPath(1L)).isEqualTo(webp);
    }

    @Test
    void webp_shouldReturnNull_whenMissing() {
        when(repo.findById(1L)).thenReturn(Optional.of(photo()));

        assertThat(resolver.getWebpPath(1L)).isNull();
    }
}
