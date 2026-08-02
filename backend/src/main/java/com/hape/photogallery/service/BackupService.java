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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 备份导出：筛选照片 → 收集元数据（事务内，懒加载初始化后转纯 DTO）→ 流式打包 tar.gz。
 *
 * 打包结构（photo-gallery-backup-YYYY-MM-DD.tar.gz）：
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
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    public static final String METADATA_VERSION = "1.0";

    private final PhotoRepository photoRepo;
    private final TagRepository tagRepo;
    private final CategoryRepository catRepo;
    private final AlbumRepository albumRepo;
    private final StorageService storage;
    private final ObjectMapper objectMapper;

    public BackupService(PhotoRepository photoRepo, TagRepository tagRepo,
                         CategoryRepository catRepo, AlbumRepository albumRepo,
                         StorageService storage, ObjectMapper objectMapper) {
        this.photoRepo = photoRepo;
        this.tagRepo = tagRepo;
        this.catRepo = catRepo;
        this.albumRepo = albumRepo;
        this.storage = storage;
        this.objectMapper = objectMapper;
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
     * 将备份内容流式写入 tar.gz。照片文件缺失时跳过并记录 warn（磁盘与 DB 不一致的容错）。
     * 目录条目先于文件写入，保证 Win11 原生 tar 等解压器兼容。
     */
    public void writeTo(OutputStream out, BackupBundle bundle) throws IOException {
        try (GzipCompressorOutputStream gz = new GzipCompressorOutputStream(out);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            BackupMetadata metadata = new BackupMetadata(METADATA_VERSION, bundle.exportedAt(),
                    bundle.photos().size(), bundle.filters());
            writeJsonEntry(tar, "database/metadata.json", metadata);
            writeJsonEntry(tar, "database/photos.json", bundle.photos());
            writeJsonEntry(tar, "database/exif.json", bundle.exif());
            writeJsonEntry(tar, "database/tags.json", bundle.tags());
            writeJsonEntry(tar, "database/categories.json", bundle.categories());
            writeJsonEntry(tar, "database/albums.json", bundle.albums());

            // 收集存在的文件并写目录条目
            Set<String> dirs = new TreeSet<>();
            List<Path> files = new ArrayList<>();
            for (BackupPhoto photo : bundle.photos()) {
                Path file = storage.resolveSafe(photo.fileName());
                if (!Files.isRegularFile(file)) {
                    log.warn("备份跳过缺失文件: {}", photo.fileName());
                    continue;
                }
                files.add(file);
                String rel = "photos/" + photo.fileName();
                int slash = rel.lastIndexOf('/');
                while (slash > 0) {
                    dirs.add(rel.substring(0, slash));
                    slash = rel.lastIndexOf('/', slash - 1);
                }
            }
            for (String dir : dirs) {
                TarArchiveEntry entry = new TarArchiveEntry(dir + "/");
                tar.putArchiveEntry(entry);
                tar.closeArchiveEntry();
            }
            for (Path file : files) {
                String entryName = "photos/" + storage.getUploadDir().relativize(file).toString().replace('\\', '/');
                TarArchiveEntry entry = new TarArchiveEntry(entryName);
                entry.setSize(Files.size(file));
                entry.setModTime(Files.getLastModifiedTime(file));
                tar.putArchiveEntry(entry);
                Files.copy(file, tar);
                tar.closeArchiveEntry();
            }
            tar.finish();
        }
    }

    private void writeJsonEntry(TarArchiveOutputStream tar, String name, Object value) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        byte[] bytes = objectMapper.writeValueAsBytes(value);
        entry.setSize(bytes.length);
        tar.putArchiveEntry(entry);
        tar.write(bytes);
        tar.closeArchiveEntry();
    }
}
