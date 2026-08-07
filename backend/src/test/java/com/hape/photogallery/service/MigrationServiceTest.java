package com.hape.photogallery.service;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.ProcessingStatus;
import com.hape.photogallery.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 迁移服务（拆分自 PhotoService）：分页游标遍历，空库/空数据安全返回 0；补生成只扫 DONE 照片 */
@ExtendWith(MockitoExtension.class)
class MigrationServiceTest {

    @Mock private PhotoRepository photoRepo;
    @Mock private ImageProcessingService imageService;
    @Mock private StorageService storage;
    @Mock private ExifService exifService;

    @TempDir
    Path tempDir;

    private MigrationService service;

    @BeforeEach
    void setUp() {
        service = new MigrationService(photoRepo, imageService, storage, exifService,
                new FilePathResolver(photoRepo, storage));
    }

    @Test
    void extractExifForExisting_emptyDb_shouldReturnZero() {
        when(photoRepo.findAll(any(PageRequest.class))).thenReturn(Page.empty());
        assertThat(service.extractExifForExisting()).isZero();
    }

    @Test
    void migrateThumbnails_emptyDb_shouldReturnZero() {
        when(storage.getUploadDir()).thenReturn(mock(Path.class));
        when(photoRepo.findByProcessingStatus(any(), any(PageRequest.class))).thenReturn(Page.empty());
        assertThat(service.migrateThumbnails()).isZero();
    }

    @Test
    void migrateWebp_emptyDb_shouldReturnZero() {
        when(storage.getUploadDir()).thenReturn(mock(Path.class));
        when(photoRepo.findByProcessingStatus(any(), any(PageRequest.class))).thenReturn(Page.empty());
        assertThat(service.migrateWebp()).isZero();
    }

    @Test
    void migrateThumbnails_existingThumb_shouldSkip() throws IOException {
        // DONE 照片且缩略图已存在 → 跳过，不调用 imageService（幂等）
        Photo p = photo("2026/07/a.jpg");
        Files.createDirectories(tempDir.resolve("2026/07/thumbnails"));
        Files.write(tempDir.resolve("2026/07/thumbnails/a.jpg"), new byte[]{1});
        Files.write(tempDir.resolve("2026/07/a.jpg"), new byte[]{2}); // 原图存在
        when(storage.getUploadDir()).thenReturn(tempDir);
        when(photoRepo.findByProcessingStatus(any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 100), 1));

        assertThat(service.migrateThumbnails()).isZero();
        verify(imageService, never()).generateThumbnail(any(Path.class), any(String.class), any(String.class));
    }

    @Test
    void migrateThumbnails_missingThumb_shouldGenerate() throws IOException {
        // DONE 照片、原图存在、缩略图缺失 → 生成并计数
        Photo p = photo("2026/07/b.jpg");
        Files.createDirectories(tempDir.resolve("2026/07/thumbnails"));
        Files.write(tempDir.resolve("2026/07/b.jpg"), new byte[]{2});
        when(storage.getUploadDir()).thenReturn(tempDir);
        when(photoRepo.findByProcessingStatus(any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 100), 1));
        // generateThumbnail(Path,...) 是 void —— when() 不可 stub，用 doAnswer
        doAnswer(inv -> {
            Files.write(tempDir.resolve("2026/07/thumbnails/b.jpg"), new byte[]{1});
            return null;
        }).when(imageService).generateThumbnail(any(Path.class), any(String.class), any(String.class));

        assertThat(service.migrateThumbnails()).isEqualTo(1);
        verify(imageService).generateThumbnail(any(Path.class), any(String.class), any(String.class));
    }

    @Test
    void migrateWebp_generateFailure_shouldContinueLoop() throws IOException {
        // 防御性 catch（与 migrateThumbnails 同构）：单张失败不中断整个迁移循环
        Photo p = photo("2026/07/b.jpg");
        Files.createDirectories(tempDir.resolve("2026/07/webp"));
        Files.write(tempDir.resolve("2026/07/b.jpg"), new byte[]{2});
        when(storage.getUploadDir()).thenReturn(tempDir);
        when(photoRepo.findByProcessingStatus(any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 100), 1));
        doThrow(new RuntimeException("disk full"))
                .when(imageService).generateWebp(any(Path.class), any(String.class), any(String.class));

        assertThat(service.migrateWebp()).isZero(); // 不中断、不计数
        verify(imageService).generateWebp(any(Path.class), any(String.class), any(String.class));
    }

    @Test
    void migrateThumbnails_shouldOnlyQueryDoneStatus() {
        // 补生成只扫 DONE（排除 PROCESSING 与处理链并发、FAILED 与损坏原图）
        when(storage.getUploadDir()).thenReturn(mock(Path.class));
        when(photoRepo.findByProcessingStatus(any(), any(PageRequest.class))).thenReturn(Page.empty());

        service.migrateThumbnails();

        ArgumentCaptor<ProcessingStatus> captor = ArgumentCaptor.forClass(ProcessingStatus.class);
        verify(photoRepo).findByProcessingStatus(captor.capture(), any(PageRequest.class));
        assertThat(captor.getValue()).isEqualTo(ProcessingStatus.DONE);
    }

    @Test
    void extractExifForExisting_delegatesPerPage() {
        Photo p = photo("2026/07/a.jpg");
        when(photoRepo.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 100), 1));
        when(storage.getUploadDir()).thenReturn(tempDir);
        when(exifService.extractForExisting(any(), any())).thenReturn(1);

        assertThat(service.extractExifForExisting()).isEqualTo(1);
    }

    private Photo photo(String fileName) {
        Photo p = new Photo();
        p.setId(1L);
        p.setFileName(fileName);
        p.setProcessingStatus(ProcessingStatus.DONE);
        return p;
    }
}
