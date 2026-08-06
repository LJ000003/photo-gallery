package com.hape.photogallery.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.PhotoRepository;

/**
 * 照片变换（从 PhotoService 拆出； 事务边界重构）：
 *  - 备份原图 → 事务外执行变换（秒级图像处理不持数据库连接）→ 事务内保存（fileSize/EXIF/save 原子）；
 *  - 任何一步失败：恢复备份原图并重生成缩略图/webp，消除「DB 回滚但磁盘已被覆盖」的不一致；
 *  - 图片无法解码/编码（损坏文件、webp native 库不可用）→ 400 业务错误而非 500。
 */
@Service
public class PhotoTransformService {

    private static final Logger log = LoggerFactory.getLogger(PhotoTransformService.class);

    private final PhotoRepository repo;
    private final ImageProcessingService imageService;
    private final StorageService storage;
    private final ExifService exifService;
    private final TransactionTemplate transactionTemplate;
    private final FilePathResolver filePathResolver;

    public PhotoTransformService(PhotoRepository repo, ImageProcessingService imageService,
                                 StorageService storage, ExifService exifService,
                                 TransactionTemplate transactionTemplate,
                                 FilePathResolver filePathResolver) {
        this.repo = repo;
        this.imageService = imageService;
        this.storage = storage;
        this.exifService = exifService;
        this.transactionTemplate = transactionTemplate;
        this.filePathResolver = filePathResolver;
    }

    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    public void transformPhoto(Long id, int rotate, String mirror, Double cx, Double cy, Double cw, Double ch) throws IOException {
        Photo photo = repo.findById(id)
                .orElseThrow(() -> new BusinessException(404, "该照片已被删除或不存在"));
        Path filePath = storage.getUploadDir().resolve(photo.getFileName());
        if (!Files.exists(filePath)) return;

        Path backup = filePath.resolveSibling(filePath.getFileName() + ".bak");
        boolean backedUp = false;
        try {
            Files.copy(filePath, backup, StandardCopyOption.REPLACE_EXISTING);
            backedUp = true;

            doTransformPhoto(photo, filePath, rotate, mirror, cx, cy, cw, ch);
            // 变换后文件大小（事务外 stat，避免 checked 异常进回调）
            long newFileSize = Files.size(filePath);

            transactionTemplate.execute(status -> {
                // 重查拿受管实体（上面的查询事务已结束，photo 已脱管）
                Photo managed = repo.findById(id)
                        .orElseThrow(() -> new BusinessException(404, "该照片已被删除或不存在"));
                managed.setFileSize(newFileSize);
                // transform 重写原图（可能裁掉/翻转水印）→ 复位 original_processed，
                // 下次 retry/重扫处理时重新打水印（V12 幂等标记联动）
                managed.setOriginalProcessed(false);
                // EXIF 移入事务：save 失败回滚时 EXIF 同步回滚，与恢复的原图保持一致
                try {
                    exifService.extractAndSave(managed, filePath);
                } catch (Exception e) {
                    // EXIF 失败非致命（与现状一致，仅告警）
                    log.warn("变换后 EXIF 提取失败 photo={}: {}", id, e.getMessage());
                }
                repo.save(managed);
                return null;
            });
        } catch (IOException | RuntimeException e) {
            // 补偿：恢复原图 + 从原图重生成缩略图/webp（它们已被变换图覆盖）
            if (backedUp && Files.exists(backup)) {
                try {
                    Files.copy(backup, filePath, StandardCopyOption.REPLACE_EXISTING);
                    // 用上面加载的脱管实体读标量 fileName（无需重查）
                    FilePathResolver.FilePathParts parts = filePathResolver.parseFilePath(photo.getFileName());
                    imageService.generateThumbnail(filePath, parts.dateDir(), parts.baseName());
                    imageService.generateThumbnail(filePath, parts.dateDir(), parts.baseName(), 200);
                    imageService.generateWebp(filePath, parts.dateDir(), parts.baseName());
                    log.warn("Transform 失败已回滚恢复原图 photo={}", id);
                } catch (IOException restore) {
                    log.error("Transform 补偿恢复失败 photo={}: {}", id, restore.getMessage());
                }
            }
            if (e instanceof IOException || e instanceof UncheckedIOException) {
                // 图片无法解码/写入编码失败（含 getFormat 读文件失败包装的
                // UncheckedIOException——它不是 IOException 子类，需显式兜底）——
                // "用户图片不可处理"，返回业务错误而非 500
                log.warn("Transform failed for photo {}: {}", id, e.getMessage());
                throw new BusinessException(400, "图片无法处理，可能已损坏");
            }
            throw e;
        } finally {
            if (backedUp) {
                try {
                    Files.deleteIfExists(backup);
                } catch (IOException ignored) {
                    // 备份清理失败不阻断主流程
                }
            }
        }
    }

    /** 事务外执行变换：photo 仅用于读标量 fileName 推导相对路径，脱管实体访问安全 */
    private void doTransformPhoto(Photo photo, Path filePath, int rotate, String mirror,
                                  Double cx, Double cy, Double cw, Double ch) throws IOException {
        BufferedImage img = imageService.decodeCapped(filePath);
        if (img == null) {
            throw new IOException("ImageIO.read returned null");
        }

        if (cx != null && cy != null && cw != null && ch != null
                && cw > 0 && ch > 0 && cw < 1 && ch < 1) {
            int x = (int) (img.getWidth() * cx);
            int y = (int) (img.getHeight() * cy);
            int w = (int) (img.getWidth() * cw);
            int h = (int) (img.getHeight() * ch);
            x = Math.max(0, Math.min(x, img.getWidth() - 1));
            y = Math.max(0, Math.min(y, img.getHeight() - 1));
            w = Math.max(1, Math.min(w, img.getWidth() - x));
            h = Math.max(1, Math.min(h, img.getHeight() - y));
            img = img.getSubimage(x, y, w, h);
        }

        if (rotate > 0) {
            img = imageService.rotateImage(img, rotate % 360);
        }

        if ("horizontal".equals(mirror)) {
            img = imageService.mirrorImage(img, true);
        } else if ("vertical".equals(mirror)) {
            img = imageService.mirrorImage(img, false);
        }

        String format = imageService.getFormat(filePath);
        if (!ImageIO.write(img, format, filePath.toFile())) {
            // 无对应 ImageWriter（如 webp 原图 native 库加载失败）→ 明确失败，而非静默不改文件
            throw new IOException("No ImageWriter for format " + format);
        }

        FilePathResolver.FilePathParts parts = filePathResolver.parseFilePath(photo.getFileName());
        imageService.generateThumbnail(filePath, parts.dateDir(), parts.baseName());
        imageService.generateThumbnail(filePath, parts.dateDir(), parts.baseName(), 200);
        imageService.generateWebp(filePath, parts.dateDir(), parts.baseName());
    }
}
