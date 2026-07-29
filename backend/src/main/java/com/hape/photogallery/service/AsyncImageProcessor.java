package com.hape.photogallery.service;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.repository.PhotoRepository;

@Component
public class AsyncImageProcessor {

    private static final Logger log = LoggerFactory.getLogger(AsyncImageProcessor.class);

    private final PhotoRepository photoRepo;
    private final ImageProcessingService imageService;
    private final ExifService exifService;
    private final StorageService storage;

    public AsyncImageProcessor(PhotoRepository photoRepo,
                                ImageProcessingService imageService,
                                ExifService exifService,
                                StorageService storage) {
        this.photoRepo = photoRepo;
        this.imageService = imageService;
        this.exifService = exifService;
        this.storage = storage;
    }

    @Async("imageTaskExecutor")
    @Transactional
    public void process(Photo photo, Path target, String dateDir, String baseName, String watermark) {
        try {
            // EXIF 提取（非致命）
            try {
                exifService.extractAndSave(photo, target);
            } catch (Exception e) {
                log.debug("EXIF 提取失败 (photo={}): {}", photo.getId(), e.getMessage());
            }

            // 自动旋转（根据 EXIF Orientation 修正方向）
            imageService.autoRotateIfNeeded(target);

            // 水印
            if (watermark != null && !watermark.isBlank()) {
                imageService.applyWatermark(target, watermark);
            }

            // 缩略图（400px + 200px）
            imageService.generateThumbnail(target, dateDir, baseName);
            imageService.generateThumbnail(target, dateDir, baseName, 200);

            // WebP 转换
            imageService.generateWebp(target, dateDir, baseName);

            // 标记完成
            photo.setProcessingStatus("DONE");
            photo.setErrorMessage(null);
            photoRepo.save(photo);
        } catch (Exception e) {
            log.error("异步图片处理失败 (photo={}): {}", photo.getId(), e.getMessage(), e);
            photo.setProcessingStatus("FAILED");
            photo.setErrorMessage(e.getMessage() != null ? e.getMessage() : "未知错误");
            photoRepo.save(photo);
        }
    }
}
