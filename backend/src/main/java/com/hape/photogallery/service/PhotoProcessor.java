package com.hape.photogallery.service;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.ProcessingStatus;
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

            // 2. 解码原图（降采样上限 4096px——448m 堆下全尺寸解码超大图会 OOM）
            BufferedImage img = imageService.decodeCapped(target);
            if (img == null) {
                // 确定性终态例外（不走「状态上移」）：文件损坏重试/重扫必然同样失败，
                // 直接落 FAILED 避免进 rabbit 重试循环；与 catch 路径指标语义对齐
                processingFailureCounter.increment();
                log.warn("  无法解码图片 photo={}", photoId);
                photo.setProcessingStatus(ProcessingStatus.FAILED);
                photo.setErrorMessage("无法解码图片文件");
                photoRepo.save(photo);
                return;
            }

            // 3. 自动旋转
            log.debug("  [2/5] 自动旋转 photo={}", photoId);
            BufferedImage processed = imageService.autoRotateIfNeeded(img, target);

            // 4-5. 水印 + 写回原图（V12 幂等：original_processed=true 则跳过——重试/重扫
            // 若重走全链会二次解码「已带水印的原图」再画一层，水印逐层加深。写回成功立即
            // 落中间态 save：此后崩溃/失败，重试直接跳过本段，防水印叠加与二次有损重编码）
            boolean alreadyProcessed = photo.isOriginalProcessed();
            boolean hasWatermark = watermark != null && !watermark.isBlank();
            if (!alreadyProcessed) {
                if (hasWatermark) {
                    log.debug("  [3/5] 水印 photo={}", photoId);
                    imageService.applyWatermark(processed, watermark);
                }
                if (processed != img || hasWatermark) {
                    log.debug("  写回原图 photo={}", photoId);
                    imageService.writeOriginalJpeg(processed, target);
                    photo.setOriginalProcessed(true);
                    photoRepo.save(photo);
                }
            }

            // 6. 缩略图
            log.debug("  [4/5] 缩略图 photo={}", photoId);
            BufferedImage display = imageService.downscaleToDisplay(processed);
            imageService.generateThumbnail(display, dateDir, baseName);
            imageService.generateThumbnail(display, dateDir, baseName, 200);

            // 7. WebP
            log.debug("  [5/5] WebP 转换 photo={}", photoId);
            imageService.generateWebp(display, dateDir, baseName);

            photo.setProcessingStatus(ProcessingStatus.DONE);
            photo.setErrorMessage(null);
            photoRepo.save(photo);
            log.info("处理完成 photo={}", photoId);
        } catch (Throwable e) {
            processingFailureCounter.increment();
            log.error("处理失败 photo={}: {}", photoId, e.getMessage(), e);
            // 状态写入上移到调用方（P2 修复）：rabbit 模式由 consumer 在重试耗尽后落 FAILED
            // （重试期间不落 FAILED，避免「FAILED → 重试成功翻回 DONE」的状态翻转 + 前端
            // 误显重试按钮）；dev 模式由 AsyncImageProcessor 落 FAILED 终态。
            // 这里只做缓存失效（@CacheEvict 后置在异常时不生效，缓存最长 30s 显示 PROCESSING）
            evictListCaches();
            // wrapper 带根因 message：dev/rabbit 两个调用方都取 getMessage() 落 errorMessage，
            // 不带根因前端只看到固定文案（回归教训，勿再省略）
            throw new RuntimeException("Photo processing failed for photo=" + photoId
                    + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }
}
