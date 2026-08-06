package com.hape.photogallery.service;

import com.hape.photogallery.entity.ExifData;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.ExifDataRepository;
import com.hape.photogallery.repository.PhotoRepository;
import com.hape.photogallery.service.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回收站服务测试（从 PhotoServiceTest 拆出）：
 * 恢复/永久删除/30 天定时清理。用例自原 PhotoServiceTest 原样搬移，不删改断言。
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TrashServiceTest {

    @Mock private PhotoRepository photoRepo;
    @Mock private ExifDataRepository exifRepo;
    @Mock private StorageService storage;
    @Mock private AlbumService albumService;
    @Mock private PhotoQueryService photoQueryService;

    private TrashService service;

    @BeforeEach
    void setUp() {
        // 真实 FilePathResolver：删除文件走 storage.deleteFile（原图 + 缩略图×2 + webp）
        service = new TrashService(photoRepo, exifRepo, new FilePathResolver(photoRepo, storage),
                albumService, photoQueryService);
    }

    @Test
    void restore_notDeleted_should404() {
        when(photoRepo.findDeletedById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到可恢复的照片");
        verify(photoRepo, never()).save(any());
    }

    @Test
    void restore_success_shouldClearDeletedAt() {
        Photo p = new Photo();
        p.setId(1L);
        p.setName("已删");
        p.setDeletedAt(LocalDateTime.now());
        when(photoRepo.findDeletedById(1L)).thenReturn(Optional.of(p));

        service.restore(1L);

        assertThat(p.getDeletedAt()).isNull();
        verify(photoRepo).save(p);
    }

    @Test
    void permanentlyDelete_shouldDeleteExifAndFiles() {
        Photo p = new Photo();
        p.setId(1L);
        p.setName("已删");
        p.setFileName("2026/07/test.jpg");
        when(photoRepo.findDeletedById(1L)).thenReturn(Optional.of(p));
        ExifData exif = new ExifData();
        when(exifRepo.findByPhoto_Id(1L)).thenReturn(Optional.of(exif));

        service.permanentlyDelete(1L);

        verify(exifRepo).delete(exif);
        verify(photoRepo).delete(p);
        // 真实 FilePathResolver：原图 + 缩略图(含 200) + webp 四文件清理
        verify(storage, times(4)).deleteFile(any());
        // 封面重选接入点（硬删路径）——与软删路径同标准验证，防回归
        verify(albumService).reselectCoversAfterPhotoDeletion(1L);
    }

    @Test
    void permanentlyDelete_notFound_should404() {
        when(photoRepo.findDeletedById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.permanentlyDelete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到该照片");
        verify(photoRepo, never()).delete(any());
    }

    @Test
    void cleanupDeletedPermanently_shouldPurgeExpired() {
        Photo p1 = new Photo();
        p1.setId(1L);
        p1.setFileName("2026/07/a.jpg");
        Photo p2 = new Photo();
        p2.setId(2L);
        p2.setFileName("2026/07/b.jpg");
        when(photoRepo.findDeletedBefore(any())).thenReturn(List.of(p1, p2));

        service.cleanupDeletedPermanently();

        verify(photoRepo).delete(p1);
        verify(photoRepo).delete(p2);
        verify(storage, atLeast(8)).deleteFile(any());
        // 30 天定时清理的封面重选接入点——防回归
        verify(albumService).reselectCoversAfterPhotoDeletion(1L);
        verify(albumService).reselectCoversAfterPhotoDeletion(2L);
    }

    @Test
    void cleanupDeletedPermanently_emptyList_shouldNoOp() {
        when(photoRepo.findDeletedBefore(any())).thenReturn(List.of());

        service.cleanupDeletedPermanently();

        verify(photoRepo, never()).delete(any());
        verify(exifRepo, never()).delete(any());
    }
}
