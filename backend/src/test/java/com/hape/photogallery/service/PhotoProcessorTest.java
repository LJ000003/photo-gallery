package com.hape.photogallery.service;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.repository.PhotoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhotoProcessorTest {

    @Mock private PhotoRepository photoRepo;
    @Mock private ImageProcessingService imageService;
    @Mock private ExifService exifService;
    @Mock private CacheManager cacheManager;

    private PhotoProcessor processor;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        processor = new PhotoProcessor(photoRepo, imageService, exifService, cacheManager);
    }

    @Test
    void process_photoNotFound_shouldReturnEarly() throws Exception {
        when(photoRepo.findById(1L)).thenReturn(Optional.empty());

        processor.process(1L, tempDir.resolve("nope.jpg"), "2024/08", "nope.jpg", null);

        verify(photoRepo, never()).save(any());
    }

    @Test
    void process_failure_shouldManuallyEvictListCaches() throws Exception {
        Photo photo = new Photo();
        photo.setId(1L);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));
        // 解码返回 null → 走「无法解码」分支：save FAILED 后正常 return（后置 @CacheEvict 生效）
        Path target = tempDir.resolve("bad.jpg");
        Files.writeString(target, "not an image");
        when(imageService.decodeCapped(any())).thenReturn(null);

        processor.process(1L, target, "2024/08", "bad.jpg", null);

        verify(photoRepo, times(1)).save(any(Photo.class));
    }

    @Test
    void process_throwable_shouldManuallyEvictListCaches() throws Exception {
        Photo photo = new Photo();
        photo.setId(1L);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));
        // 合法图片 → 流程走到 autoRotateIfNeeded 才抛异常
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Path target = tempDir.resolve("x.jpg");
        Files.writeString(target, "x");
        when(imageService.decodeCapped(any())).thenReturn(img);
        when(imageService.autoRotateIfNeeded(any(), any())).thenThrow(new RuntimeException("boom"));
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("photos")).thenReturn(cache);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> processor.process(1L, target, "2024/08", "x.jpg", null));

        // 失败路径手动清缓存（@CacheEvict 后置在异常时不生效）
        verify(cacheManager, times(1)).getCache("photos");
        // 状态写入已上移调用方（P2 修复核心契约）：普通失败不得由 processor 落 FAILED——
        // 曾在此落 FAILED 与 rabbit 重试并存导致状态翻转，此断言防回归
        verify(photoRepo, never()).save(any(Photo.class));
    }

    @Test
    void process_validImage_shouldComplete() throws Exception {
        Photo photo = new Photo();
        photo.setId(1L);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));

        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Path target = tempDir.resolve("x.jpg");
        Files.writeString(target, "x");
        when(imageService.decodeCapped(any())).thenReturn(img);
        when(imageService.autoRotateIfNeeded(any(), any())).thenReturn(img);
        when(imageService.downscaleToDisplay(any())).thenReturn(img);

        processor.process(1L, target, "2024/08", "x.jpg", null);

        // 无水印且无旋转（processed == img）→ 不写回、不置 flag，仅 DONE 一次 save
        verify(imageService, never()).writeOriginalJpeg(any(), any());
        verify(photoRepo, times(1)).save(any(Photo.class));
    }

    // ==================== V12 水印幂等（originalProcessed 分支） ====================

    @Test
    void process_originalProcessedTrue_shouldSkipWatermarkAndWriteBack() throws Exception {
        Photo photo = new Photo();
        photo.setId(1L);
        photo.setOriginalProcessed(true); // 原图已处理过（写回已带水印）→ 重试不得再打水印
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));

        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Path target = tempDir.resolve("x.jpg");
        Files.writeString(target, "x");
        when(imageService.decodeCapped(any())).thenReturn(img);
        when(imageService.autoRotateIfNeeded(any(), any())).thenReturn(img);
        when(imageService.downscaleToDisplay(any())).thenReturn(img);

        processor.process(1L, target, "2024/08", "x.jpg", "我的水印");

        // 核心断言：水印/写回被跳过（水印叠加 = 用户内容被重复篡改）
        verify(imageService, never()).applyWatermark(any(BufferedImage.class), anyString());
        verify(imageService, never()).writeOriginalJpeg(any(), any());
        // 缩略图/webp 仍生成，仅 DONE 一次 save
        verify(imageService, times(1)).generateThumbnail(any(BufferedImage.class), anyString(), anyString());
        verify(imageService, times(1)).generateThumbnail(any(BufferedImage.class), anyString(), anyString(), anyInt());
        verify(imageService, times(1)).generateWebp(any(BufferedImage.class), anyString(), anyString());
        verify(photoRepo, times(1)).save(any(Photo.class));
    }

    @Test
    void process_originalProcessedFalse_shouldSaveFlagAfterWriteBack() throws Exception {
        Photo photo = new Photo();
        photo.setId(1L);
        photo.setOriginalProcessed(false);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));

        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Path target = tempDir.resolve("x.jpg");
        Files.writeString(target, "x");
        when(imageService.decodeCapped(any())).thenReturn(img);
        when(imageService.autoRotateIfNeeded(any(), any())).thenReturn(img);
        when(imageService.downscaleToDisplay(any())).thenReturn(img);

        processor.process(1L, target, "2024/08", "x.jpg", "我的水印");

        // 有水印 → 打水印 + 写回，且写回后立即 save 置 flag（中途 save：此后崩溃重试跳过水印）
        verify(imageService, times(1)).applyWatermark(any(BufferedImage.class), eq("我的水印"));
        verify(imageService, times(1)).writeOriginalJpeg(any(), any());
        // flag 中途 save + DONE save：写回时已置位，两次 save 实体均带 flag=true
        verify(photoRepo, times(2)).save(argThat(p -> ((Photo) p).isOriginalProcessed()));
    }
}
