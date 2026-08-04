package com.hape.photogallery.service;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.repository.PhotoRepository;

/**
 * 图片处理核心逻辑，从 AsyncImageProcessor 提取。
 * 无 @Async、无 @Transactional —— 由调用方决定事务和异步策略。
 * dev: AsyncProcessingSender (线程池) 调用
 * prod: PhotoProcessingConsumer (RabbitMQ) 调用
 *
 * evict 对照表（聚合根 → 依赖缓存）：处理完成置 DONE/FAILED 后，photos 列表的
 * processingStatus 会变 → 失效 {photos, timeline, map, stats}（与 PhotoService
 * 写操作清单一致）。成功路径靠 @CacheEvict 后置生效；失败路径（rethrow）不触发
 * 注解 evict，由 catch 块手动清（否则缓存最长 30s 显示 PROCESSING，看不到重试按钮）。
 */
@Component
public class PhotoProcessor {

    private static final Logger log = LoggerFactory.getLogger(PhotoProcessor.class);

    private static final String[] EVICT_CACHES = {"photos", "timeline", "map", "stats"};

    private final PhotoRepository photoRepo;
    private final ImageProcessingService imageService;
    private final ExifService exifService;
    private final CacheManager cacheManager;

    private final Counter processingCounter = Metrics.counter("photo.processing.total");
    private final Counter processingFailureCounter = Metrics.counter("photo.processing.failures");

    public PhotoProcessor(PhotoRepository photoRepo,
                          ImageProcessingService imageService,
                          ExifService exifService,
                          CacheManager cacheManager) {
        this.photoRepo = photoRepo;
        this.imageService = imageService;
        this.exifService = exifService;
        this.cacheManager = cacheManager;
    }

    private void evictListCaches() {
        for (String name : EVICT_CACHES) {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
    }

    /** 执行完整的图片处理管线（EXIF → 旋转 → 水印 → 缩略图 → WebP） */
    @Timed(value = "photo.processing.time", description = "Photo processing duration")
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    public void process(Long photoId, Path target, String dateDir, String baseName, String watermark) {
        log.info("开始处理 photo={}", photoId);
        processingCounter.increment();
        Photo photo = photoRepo.findById(photoId).orElse(null);
        if (photo == null) {
            log.warn("处理中止，照片不存在 photo={}", photoId);
            return;
        }
        try {
            // 1. EXIF 提取（非致命失败）
            try {
                log.debug("  [1/5] EXIF 提取 photo={}", photoId);
                exifService.extractAndSave(photo, target);
            } catch (Exception e) {
                log.warn("  EXIF 提取失败 photo={}: {}", photoId, e.getMessage());
            }

            // 2. 解码原图
            BufferedImage img = ImageIO.read(target.toFile());
            if (img == null) {
                log.warn("  无法解码图片 photo={}", photoId);
                photo.setProcessingStatus("FAILED");
                photo.setErrorMessage("无法解码图片文件");
                photoRepo.save(photo);
                return;
            }

            // 3. 自动旋转
            log.debug("  [2/5] 自动旋转 photo={}", photoId);
            BufferedImage processed = imageService.autoRotateIfNeeded(img, target);

            // 4. 水印
            boolean hasWatermark = watermark != null && !watermark.isBlank();
            if (hasWatermark) {
                log.debug("  [3/5] 水印 photo={}", photoId);
                imageService.applyWatermark(processed, watermark);
            }

            // 5. 写回原图（如果被修改）
            if (processed != img || hasWatermark) {
                log.debug("  写回原图 photo={}", photoId);
                imageService.writeOriginalJpeg(processed, target);
            }

            // 6. 缩略图
            log.debug("  [4/5] 缩略图 photo={}", photoId);
            BufferedImage display = imageService.downscaleToDisplay(processed);
            imageService.generateThumbnail(display, dateDir, baseName);
            imageService.generateThumbnail(display, dateDir, baseName, 200);

            // 7. WebP
            log.debug("  [5/5] WebP 转换 photo={}", photoId);
            imageService.generateWebp(display, dateDir, baseName);

            photo.setProcessingStatus("DONE");
            photo.setErrorMessage(null);
            photoRepo.save(photo);
            log.info("处理完成 photo={}", photoId);
        } catch (Throwable e) {
            processingFailureCounter.increment();
            log.error("处理失败 photo={}: {}", photoId, e.getMessage(), e);
            try {
                String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
                if (msg.length() > 500) msg = msg.substring(0, 497) + "...";
                photo.setProcessingStatus("FAILED");
                photo.setErrorMessage(msg);
                photoRepo.save(photo);
            } catch (Throwable inner) {
                log.error("无法保存失败状态 photo={}", photoId, inner);
            }
            // 失败路径已置 FAILED 并落库，手动失效列表缓存（@CacheEvict 后置在异常时不生效）
            evictListCaches();
            throw new RuntimeException("Photo processing failed for photo=" + photoId, e);
        }
    }
}
