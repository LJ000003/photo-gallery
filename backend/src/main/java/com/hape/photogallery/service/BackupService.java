package com.hape.photogallery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hape.photogallery.dto.BackupExportRequest;
import com.hape.photogallery.entity.Album;
import com.hape.photogallery.entity.Category;
import com.hape.photogallery.entity.ExifData;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.Tag;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.AlbumRepository;
import com.hape.photogallery.repository.CategoryRepository;
import com.hape.photogallery.repository.PhotoRepository;
import com.hape.photogallery.repository.TagRepository;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 备份导出：筛选照片 → 收集元数据（事务内，懒加载初始化后转纯 DTO）→ 流式打包 zip。
 *
 * 打包结构（photo-gallery-backup-YYYY-MM-DD.zip）：
 * <pre>
 *   database/metadata.json    导出版本、时间、筛选参数、照片数
 *   database/photos.json      照片元数据（含 categoryId / tagIds / albumIds 关联）
 *   database/exif.json        EXIF 信息（按 photoId 关联）
 *   database/tags.json        tags / categories.json / albums.json
 *   photos/&lt;fileName&gt;         原始文件，保持服务器目录结构（如 2024/01/uuid_x.jpg）
 * </pre>
 *
 * 设计要点：
 * - collect() 在请求线程同步执行：事务 + Hibernate.initialize 懒加载 + 空结果 400 快速失败，
 *   流式阶段（StreamingResponseBody 异步线程）只做文件 I/O，避免状态码已提交后无法改错误响应。
 * - 文件逐张流式复制，不落地临时文件、不占内存（照片量级再大也只是顺序读磁盘）。
 * - 格式为 zip（java.util.zip，零第三方依赖）：通用解压器/压缩软件均支持，
 *   替代早期 commons-compress 的 tar.gz。
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    public static final String METADATA_VERSION = "1.0";

    /** 缓存 zip 固定文件名（下载时 Content-Disposition 用新鲜日期名） */
    public static final String CACHE_FILENAME = "photo-gallery-backup-cache.zip";
    public static final String FINGERPRINT_FILENAME = "fingerprint.json";

    private final PhotoRepository photoRepo;
    private final TagRepository tagRepo;
    private final CategoryRepository catRepo;
    private final AlbumRepository albumRepo;
    private final StorageService storage;
    private final ObjectMapper objectMapper;
    private final Path backupDir;

    public BackupService(PhotoRepository photoRepo, TagRepository tagRepo,
                         CategoryRepository catRepo, AlbumRepository albumRepo,
                         StorageService storage, ObjectMapper objectMapper,
                         @Value("${photo.backup-dir:${user.home}/photo-backups}") String backupDir) {
        this.photoRepo = photoRepo;
        this.tagRepo = tagRepo;
        this.catRepo = catRepo;
        this.albumRepo = albumRepo;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.backupDir = Paths.get(backupDir).toAbsolutePath().normalize();
    }

    // === 元数据 DTO（纯数据，事务外安全使用） ===

    public record BackupPhoto(Long id, String name, String description, String fileName,
                              String originalFileName, Long fileSize, String contentType,
                              LocalDateTime createdAt, String processingStatus, String fileHash,
                              Long categoryId, List<Long> tagIds, List<Long> albumIds) {
        static BackupPhoto from(Photo p) {
            return new BackupPhoto(p.getId(), p.getName(), p.getDescription(), p.getFileName(),
                    p.getOriginalFileName(), p.getFileSize(), p.getContentType(), p.getCreatedAt(),
                    p.getProcessingStatus(), p.getFileHash(),
                    p.getCategory() != null ? p.getCategory().getId() : null,
                    p.getTags().stream().map(Tag::getId).sorted().toList(),
                    p.getAlbums().stream().map(Album::getId).sorted().toList());
        }
    }

    public record BackupExif(Long photoId, LocalDateTime dateTaken, String cameraModel,
                             String lensModel, String focalLength, String aperture,
                             String shutterSpeed, Integer iso,
                             Double latitude, Double longitude, Double altitude) {
        static BackupExif from(ExifData e) {
            return new BackupExif(e.getPhotoId(), e.getDateTaken(), e.getCameraModel(), e.getLensModel(),
                    e.getFocalLength(), e.getAperture(), e.getShutterSpeed(), e.getIso(),
                    e.getLatitude(), e.getLongitude(), e.getAltitude());
        }
    }

    public record BackupTag(Long id, String name, String color) {}
    public record BackupCategory(Long id, String name) {}
    public record BackupAlbum(Long id, String name, String description, Long coverPhotoId, LocalDateTime createdAt) {}

    public record BackupFilters(Long albumId, Long categoryId, String dateFrom, String dateTo) {}
    public record BackupMetadata(String version, LocalDateTime exportedAt, int photoCount, BackupFilters filters) {}

    /** collect() 的产物：全部为纯 DTO，可在事务外任意使用 */
    public record BackupBundle(List<BackupPhoto> photos, List<BackupExif> exif,
                               List<BackupTag> tags, List<BackupCategory> categories,
                               List<BackupAlbum> albums, LocalDateTime exportedAt,
                               BackupFilters filters) {}

    // === 收集 ===

    /**
     * 事务内收集备份元数据并初始化懒加载；照片为空时抛 400（快速失败，不生成空包）。
     */
    @Transactional(readOnly = true)
    public BackupBundle collect(BackupExportRequest req) {
        LocalDateTime dateFrom = parseDate(req.getDateFrom(), true);
        LocalDateTime dateTo = parseDate(req.getDateTo(), false);

        List<Photo> photos = photoRepo.findForBackup(req.getAlbumId(), req.getCategoryId(),
                dateFrom, dateTo);
        if (photos.isEmpty()) {
            throw new BusinessException(400, "没有符合条件的照片");
        }

        // 事务内完成懒加载初始化，事务外只碰纯 DTO，避免 LazyInitializationException
        List<BackupPhoto> photoRecords = new ArrayList<>(photos.size());
        List<BackupExif> exifRecords = new ArrayList<>();
        for (Photo p : photos) {
            Hibernate.initialize(p.getTags());
            Hibernate.initialize(p.getAlbums());
            ExifData exif = p.getExifData();
            if (exif != null) {
                Hibernate.initialize(exif);
                exifRecords.add(BackupExif.from(exif));
            }
            photoRecords.add(BackupPhoto.from(p));
        }

        return new BackupBundle(photoRecords, exifRecords,
                tagRepo.findAll().stream().map(t -> new BackupTag(t.getId(), t.getName(), t.getColor())).toList(),
                catRepo.findAll().stream().map(c -> new BackupCategory(c.getId(), c.getName())).toList(),
                albumRepo.findAll().stream()
                        .map(a -> new BackupAlbum(a.getId(), a.getName(), a.getDescription(),
                                a.getCoverPhotoId(), a.getCreatedAt())).toList(),
                LocalDateTime.now(),
                new BackupFilters(req.getAlbumId(), req.getCategoryId(), req.getDateFrom(), req.getDateTo()));
    }

    private LocalDateTime parseDate(String value, boolean startOfDay) {
        if (value == null || value.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(value.trim());
            return startOfDay ? d.atStartOfDay() : d.atTime(LocalTime.MAX);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "日期格式错误，应为 yyyy-MM-dd");
        }
    }

    // === 打包 ===

    /**
     * 将备份内容流式写入 zip。照片文件缺失时跳过并记录 warn（磁盘与 DB 不一致的容错）。
     * zip 的目录结构随条目隐式创建，无需显式目录条目。
     */
    public void writeTo(OutputStream out, BackupBundle bundle) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            BackupMetadata metadata = new BackupMetadata(METADATA_VERSION, bundle.exportedAt(),
                    bundle.photos().size(), bundle.filters());
            writeJsonEntry(zip, "database/metadata.json", metadata);
            writeJsonEntry(zip, "database/photos.json", bundle.photos());
            writeJsonEntry(zip, "database/exif.json", bundle.exif());
            writeJsonEntry(zip, "database/tags.json", bundle.tags());
            writeJsonEntry(zip, "database/categories.json", bundle.categories());
            writeJsonEntry(zip, "database/albums.json", bundle.albums());

            for (BackupPhoto photo : bundle.photos()) {
                Path file = storage.resolveSafe(photo.fileName());
                if (!Files.isRegularFile(file)) {
                    log.warn("备份跳过缺失文件: {}", photo.fileName());
                    continue;
                }
                String entryName = "photos/" + storage.getUploadDir().relativize(file).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(Files.getLastModifiedTime(file).toMillis());
                zip.putNextEntry(entry);
                Files.copy(file, zip);
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    private void writeJsonEntry(ZipOutputStream zip, String name, Object value) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        byte[] bytes = objectMapper.writeValueAsBytes(value);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    // === 预生成缓存（定时任务 + 导出时数据指纹比对） ===

    /**
     * 数据指纹：照片增删/恢复、标签/相册/分类计数变化都会改变指纹；
     * 改名/改描述等低频编辑不覆盖（缓存最长滞后一个定时周期，可接受）。
     */
    public record BackupFingerprint(long photoCount, long maxPhotoId, LocalDateTime maxCreatedAt,
                                    LocalDateTime maxDeletedAt,
                                    long tagCount, long categoryCount, long albumCount) {

        static BackupFingerprint current(PhotoRepository photoRepo, TagRepository tagRepo,
                                         CategoryRepository catRepo, AlbumRepository albumRepo) {
            // Spring Data 对 Object[] 返回类型会把每行包成元素：聚合查询实际返回 Object[1]{Object[3]}
            Object[] agg = photoRepo.backupAggregate(); // [count, maxId, maxCreatedAt]
            Object[] row = agg.length == 1 && agg[0] instanceof Object[] inner ? inner : agg;
            // H2/MySQL 对 datetime 列的 MAX 返回类型不同（LocalDateTime / Timestamp），统一转换
            Object created = row[2];
            LocalDateTime maxCreatedAt = created == null ? null
                    : created instanceof LocalDateTime ldt ? ldt
                    : ((java.sql.Timestamp) created).toLocalDateTime();
            return new BackupFingerprint(
                    ((Number) row[0]).longValue(),
                    row[1] != null ? ((Number) row[1]).longValue() : 0,
                    maxCreatedAt,
                    photoRepo.maxDeletedAt(),
                    tagRepo.count(), catRepo.count(), albumRepo.count());
        }
    }

    public Path getCacheFile() {
        return backupDir.resolve(CACHE_FILENAME);
    }

    /** 数据指纹是否与缓存一致（一致 = 可直接下载缓存，免实时打包） */
    public synchronized boolean isCacheFresh() {
        try {
            if (!Files.isRegularFile(getCacheFile()) || !Files.isRegularFile(backupDir.resolve(FINGERPRINT_FILENAME))) {
                return false;
            }
            BackupFingerprint cached = objectMapper.readValue(
                    backupDir.resolve(FINGERPRINT_FILENAME).toFile(), BackupFingerprint.class);
            return cached.equals(BackupFingerprint.current(photoRepo, tagRepo, catRepo, albumRepo));
        } catch (IOException e) {
            log.warn("备份缓存指纹读取失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 用当前数据刷新缓存：先写临时文件再原子 rename（避免下载到半写文件），随后更新指纹。
     * 导出接口（全量）与定时任务共用。
     */
    public synchronized void updateCache(BackupBundle bundle) throws IOException {
        Files.createDirectories(backupDir);
        Path tmp = backupDir.resolve(CACHE_FILENAME + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            writeTo(out, bundle);
        }
        Files.move(tmp, getCacheFile(), StandardCopyOption.REPLACE_EXISTING);
        objectMapper.writeValue(backupDir.resolve(FINGERPRINT_FILENAME).toFile(),
                BackupFingerprint.current(photoRepo, tagRepo, catRepo, albumRepo));
        log.info("备份缓存已刷新: {}（{} 张照片）", getCacheFile(), bundle.photos().size());
    }

    /**
     * 定时任务入口：重新收集全量备份并刷新缓存。空库/失败返回 false（不抛异常，避免调度线程中断）。
     * 必须带事务：内部 this.collect() 自调用绕不过 @Transactional 代理，懒加载初始化依赖外层事务的会话。
     * （文件 IO 在事务内——定时任务低频执行，可接受；用户导出路径不经过这里。）
     */
    @Transactional
    public boolean generateCachedBackup() {
        try {
            updateCache(collect(new BackupExportRequest()));
            return true;
        } catch (BusinessException e) {
            log.info("备份缓存跳过：{}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("备份缓存生成失败: {}", e.getMessage());
            return false;
        }
    }
}
