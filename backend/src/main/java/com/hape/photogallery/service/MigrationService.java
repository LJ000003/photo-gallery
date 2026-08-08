package com.hape.photogallery.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.ProcessingStatus;
import com.hape.photogallery.repository.PhotoRepository;

/**
 * 历史数据迁移（从 PhotoService 拆出）——缩略图/WebP 补生成、存量 EXIF 提取。
 * 分页游标遍历，幂等（已存在的产物跳过），迁移端点见 PhotoController。
 * 缩略图/WebP 补生成只处理 DONE 照片（排除 PROCESSING——处理链可能并发写同名文件；
 * 排除 FAILED——原图大概率损坏，补生成无意义）。
 */
@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    private static final int BATCH_SIZE = 100;

    private final PhotoRepository repo;
    private final ImageProcessingService imageService;
    private final StorageService storage;
    private final ExifService exifService;
    private final FilePathResolver filePathResolver;

    public MigrationService(PhotoRepository repo, ImageProcessingService imageService,
                            StorageService storage, ExifService exifService,
                            FilePathResolver filePathResolver) {
        this.repo = repo;
        this.imageService = imageService;
        this.storage = storage;
        this.exifService = exifService;
        this.filePathResolver = filePathResolver;
    }

    public int migrateThumbnails() {
        Path uploadDir = storage.getUploadDir();
        int count = 0;
        var pageable = PageRequest.of(0, BATCH_SIZE);
        Page<Photo> page;
        do {
            page = repo.findByProcessingStatus(ProcessingStatus.DONE, pageable);
            for (Photo p : page.getContent()) {
                String fn = p.getFileName();
                FilePathResolver.FilePathParts parts = filePathResolver.parseFilePath(fn);
                Path thumb = uploadDir.resolve(parts.dateDir()).resolve("thumbnails").resolve(parts.baseName());
                if (Files.exists(thumb)) continue;
                Path original = uploadDir.resolve(fn);
                if (!Files.exists(original)) continue;
                try {
                    imageService.generateThumbnail(original, parts.dateDir(), parts.baseName());
                    if (Files.exists(thumb)) count++;
                } catch (IOException e) {
                    log.warn("迁移缩略图失败 photo={}: {}", p.getId(), e.getMessage());
                }
            }
            pageable = pageable.next();
        } while (page.hasNext());
        return count;
    }

    public int migrateWebp() {
        Path uploadDir = storage.getUploadDir();
        int count = 0;
        var pageable = PageRequest.of(0, BATCH_SIZE);
        Page<Photo> page;
        do {
            page = repo.findByProcessingStatus(ProcessingStatus.DONE, pageable);
            for (Photo p : page.getContent()) {
                String fn = p.getFileName();
                FilePathResolver.FilePathParts parts = filePathResolver.parseFilePath(fn);
                Path webp = uploadDir.resolve(parts.dateDir()).resolve("webp").resolve(parts.baseName() + ".webp");
                if (Files.exists(webp)) continue;
                Path original = uploadDir.resolve(fn);
                if (!Files.exists(original)) continue;
                try {
                    imageService.generateWebp(original, parts.dateDir(), parts.baseName());
                    if (Files.exists(webp)) count++;
                } catch (RuntimeException e) {
                    // 防御性对齐 migrateThumbnails：单张失败不中断整个迁移循环
                    // （generateWebp 内部已吞 Throwable，此处兜 Runtime 异常）
                    log.warn("迁移 WebP 失败 photo={}: {}", p.getId(), e.getMessage());
                }
            }
            pageable = pageable.next();
        } while (page.hasNext());
        return count;
    }

    /**
     * 启动时异步补生成缺失的缩略图/WebP（由 StartupBackfillRunner 触发，@Async 跨 bean 代理生效，
     * 不阻塞应用启动与端口监听）。幂等：已存在跳过；只扫 DONE 照片。
     */
    @Async
    public void backfillMissingFilesOnStartup() {
        long start = System.currentTimeMillis();
        int thumbs = migrateThumbnails();
        int webps = migrateWebp();
        log.info("启动补生成完成：缩略图 {} 张、WebP {} 张，耗时 {}ms", thumbs, webps,
                System.currentTimeMillis() - start);
    }

    /** 存量批量提取 EXIF（POST /photos/extract-exif）：
     *  写 ExifData（dateTaken/GPS/相机参数）→ 必须失效 {photos, timeline, map}
     *  （同 PhotoQueryService.extractExifForPhoto 的清单；曾缺 evict，批量提取后
     *  时间线/地图/列表 EXIF 最长 30s 显示旧数据） */
    @CacheEvict(value = {"photos", "timeline", "map"}, allEntries = true)
    public int extractExifForExisting() {
        int count = 0;
        var pageable = PageRequest.of(0, BATCH_SIZE);
        Page<Photo> page;
        do {
            page = repo.findAll(pageable);
            count += exifService.extractForExisting(page.getContent(), storage.getUploadDir());
            pageable = pageable.next();
        } while (page.hasNext());
        return count;
    }
}
