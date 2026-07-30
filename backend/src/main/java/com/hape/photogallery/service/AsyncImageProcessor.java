package com.hape.photogallery.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

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

    public AsyncImageProcessor(PhotoRepository photoRepo,
                                ImageProcessingService imageService,
                                ExifService exifService) {
        this.photoRepo = photoRepo;
        this.imageService = imageService;
        this.exifService = exifService;
    }

    @Async("imageTaskExecutor")
    @Transactional
    public void process(Long photoId, Path target, String dateDir, String baseName, String watermark) {
        log.info("开始异步处理 photo={}", photoId);
        Photo photo = photoRepo.findById(photoId).orElse(null);
        if (photo == null) {
            log.warn("异步处理中止，照片不存在 photo={}", photoId);
            return;
        }
        try {
            // 1. EXIF 提取（直接读文件元数据，不解码像素）
            try {
                log.debug("  [1/5] EXIF 提取 photo={}", photoId);
                exifService.extractAndSave(photo, target);
            } catch (Exception e) {
                log.warn("  EXIF 提取失败 photo={}: {}", photoId, e.getMessage());
            }

            // 2. 解码原图（只此一次）
            BufferedImage img = ImageIO.read(target.toFile());
            if (img == null) {
                log.warn("  无法解码图片 photo={}", photoId);
                photo.setProcessingStatus("FAILED");
                photo.setErrorMessage("无法解码图片文件");
                photoRepo.save(photo);
                return;
            }

            // 3. 自动旋转（内存中完成）
            log.debug("  [2/5] 自动旋转 photo={}", photoId);
            BufferedImage processed = imageService.autoRotateIfNeeded(img, target);

            // 4. 水印（在旋转后的图上绘制）
            boolean hasWatermark = watermark != null && !watermark.isBlank();
            if (hasWatermark) {
                log.debug("  [3/5] 水印 photo={}", photoId);
                imageService.applyWatermark(processed, watermark);
            }

            // 5. 如果图片被修改（旋转或水印），高质量写回原文件
            if (processed != img || hasWatermark) {
                log.debug("  写回原图 photo={}", photoId);
                imageService.writeOriginalJpeg(processed, target);
            }

            // 6. 降采样到展示分辨率，加速后续衍生图生成
            log.debug("  [4/5] 缩略图 photo={}", photoId);
            BufferedImage display = imageService.downscaleToDisplay(processed);
            imageService.generateThumbnail(display, dateDir, baseName);
            imageService.generateThumbnail(display, dateDir, baseName, 200);

            log.debug("  [5/5] WebP 转换 photo={}", photoId);
            imageService.generateWebp(display, dateDir, baseName);

            photo.setProcessingStatus("DONE");
            photo.setErrorMessage(null);
            photoRepo.save(photo);
            log.info("异步处理完成 photo={}", photoId);
        } catch (Throwable e) {
            log.error("异步处理失败 photo={}: {}", photoId, e.getMessage(), e);
            try {
                String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
                if (msg.length() > 500) msg = msg.substring(0, 497) + "...";
                photo.setProcessingStatus("FAILED");
                photo.setErrorMessage(msg);
                photoRepo.save(photo);
            } catch (Throwable inner) {
                log.error("无法保存失败状态 photo={}", photoId, inner);
            }
        }
    }
}
