package com.hape.photogallery.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.repository.PhotoRepository;

/**
 * 历史数据迁移（从 PhotoService 拆出）——缩略图/WebP 补生成、存量 EXIF 提取。
 * 分页游标遍历，幂等（已存在的产物跳过），迁移端点见 PhotoController。
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
            page = repo.findAll(pageable);
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
            page = repo.findAll(pageable);
            for (Photo p : page.getContent()) {
                String fn = p.getFileName();
                FilePathResolver.FilePathParts parts = filePathResolver.parseFilePath(fn);
                Path webp = uploadDir.resolve(parts.dateDir()).resolve("webp").resolve(parts.baseName() + ".webp");
                if (Files.exists(webp)) continue;
                Path original = uploadDir.resolve(fn);
                if (!Files.exists(original)) continue;
                imageService.generateWebp(original, parts.dateDir(), parts.baseName());
                if (Files.exists(webp)) count++;
            }
            pageable = pageable.next();
        } while (page.hasNext());
        return count;
    }

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
