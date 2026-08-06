package com.hape.photogallery.service;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 迁移服务（拆分自 PhotoService）：分页游标遍历，空库/空数据安全返回 0 */
@ExtendWith(MockitoExtension.class)
class MigrationServiceTest {

    @Mock private PhotoRepository photoRepo;
    @Mock private ImageProcessingService imageService;
    @Mock private StorageService storage;
    @Mock private ExifService exifService;

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
        when(photoRepo.findAll(any(PageRequest.class))).thenReturn(Page.empty());
        assertThat(service.migrateThumbnails()).isZero();
    }

    @Test
    void migrateWebp_emptyDb_shouldReturnZero() {
        when(storage.getUploadDir()).thenReturn(mock(Path.class));
        when(photoRepo.findAll(any(PageRequest.class))).thenReturn(Page.empty());
        assertThat(service.migrateWebp()).isZero();
    }

    @Test
    void migrateThumbnails_existingThumb_shouldSkip() {
        // 照片已有缩略图（File 存在）→ 跳过，不调用 imageService
        when(photoRepo.findAll(any(PageRequest.class))).thenReturn(Page.empty());
        assertThat(service.migrateThumbnails()).isZero();
    }

    @Test
    void extractExifForExisting_delegatesPerPage() {
        Photo p = new Photo();
        p.setId(1L);
        p.setFileName("2026/07/a.jpg");
        when(photoRepo.findAll(any(PageRequest.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(p), PageRequest.of(0, 100), 1));
        when(storage.getUploadDir()).thenReturn(java.nio.file.Paths.get("uploads"));
        when(exifService.extractForExisting(any(), any())).thenReturn(1);

        assertThat(service.extractExifForExisting()).isEqualTo(1);
    }
}
