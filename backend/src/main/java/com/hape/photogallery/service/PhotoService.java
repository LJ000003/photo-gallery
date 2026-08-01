package com.hape.photogallery.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.PhotoUpdateRequest;
import com.hape.photogallery.dto.TimelineItem;
import com.hape.photogallery.entity.ExifData;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.exception.DuplicateException;
import com.hape.photogallery.exception.FileSizeExceededException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;

import com.hape.photogallery.messaging.ProcessingMessageSender;
import com.hape.photogallery.repository.CategoryRepository;
import com.hape.photogallery.repository.ExifDataRepository;
import com.hape.photogallery.repository.PhotoRepository;
import com.hape.photogallery.repository.TagRepository;
import com.hape.photogallery.util.CoordUtil;

@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // Prometheus metrics
    private final Counter uploadCounter = Metrics.counter("photo.upload.total");
    private final Counter uploadBytesCounter = Metrics.counter("photo.upload.bytes");

    private final PhotoRepository repo;
    private final TagRepository tagRepo;
    private final CategoryRepository catRepo;
    private final ExifDataRepository exifRepo;
    private final ExifService exifService;
    private final ImageProcessingService imageService;
    private final AlbumService albumService;
    private final StorageService storage;
    private final ProcessingMessageSender processingSender;

    public PhotoService(PhotoRepository repo, TagRepository tagRepo, CategoryRepository catRepo,
                        ExifDataRepository exifRepo,
                        ExifService exifService,
                        ImageProcessingService imageService,
                        AlbumService albumService,
                        StorageService storage,
                        ProcessingMessageSender processingSender) {
        this.repo = repo;
        this.tagRepo = tagRepo;
        this.catRepo = catRepo;
        this.exifRepo = exifRepo;
        this.exifService = exifService;
        this.imageService = imageService;
        this.albumService = albumService;
        this.storage = storage;
        this.processingSender = processingSender;
    }

    @Cacheable(value = "photos", key = "{#tagIds, #categoryIds, #pageable}")
    public Page<Photo> listAll(List<Long> tagIds, List<Long> categoryIds, Pageable pageable) {
        boolean hasTags = tagIds != null && !tagIds.isEmpty();
        boolean hasCats = categoryIds != null && !categoryIds.isEmpty();
        if (hasTags && hasCats) {
            return repo.findByCategoryIdsAndTagIds(categoryIds, tagIds, pageable);
        } else if (hasTags) {
            return repo.findByTagIds(tagIds, pageable);
        } else if (hasCats) {
            return repo.findByCategoryIds(categoryIds, pageable);
        }
        return repo.findAll(pageable);
    }

    public Page<Photo> search(String q, Pageable pageable) {
        if (q == null || q.isBlank()) return repo.findAll(pageable);
        return repo.search(q.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Photo getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new BusinessException(404, "该照片已被删除或不存在"));
    }

    @Transactional(readOnly = true)
    public PhotoResponse getPhotoResponse(Long id) {
        return toResponse(getById(id));
    }

    public Photo getByIdIncludeDeleted(Long id) {
        return repo.findById(id)
                .or(() -> repo.findDeletedById(id))
                .orElseThrow(() -> new BusinessException(404, "该照片不存在"));
    }

    public Page<Photo> findByIds(List<Long> ids, Pageable pageable) {
        return repo.findByIdIn(ids, pageable);
    }

    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map"}, allEntries = true)
    public Photo upload(MultipartFile file, String name, String description,
                        List<Long> tagIds, Long categoryId, String watermark) throws IOException {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileSizeExceededException("文件过大，请上传小于 10MB 的图片");
        }
        imageService.validateImageMagicBytes(file.getInputStream());

        String hash = computeSha256(file);
        repo.findByFileHash(hash).ifPresent(existing -> {
            eagerLoad(existing);
            throw new DuplicateException(toResponse(existing));
        });

        LocalDateTime now = LocalDateTime.now();
        String dateDir = String.format("%04d/%02d", now.getYear(), now.getMonthValue());
        Path datePath = storage.getUploadDir().resolve(dateDir);
        storage.createDirectories(datePath);

        String baseName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String storedName = dateDir + "/" + baseName;
        Path target = storage.getUploadDir().resolve(storedName);
        storage.store(file, target);

        Photo photo = new Photo();
        photo.setName(name != null && !name.isBlank() ? name : file.getOriginalFilename());
        photo.setDescription(description);
        photo.setFileName(storedName);
        photo.setOriginalFileName(file.getOriginalFilename());
        photo.setFileSize(file.getSize());
        photo.setContentType(file.getContentType());
        photo.setCreatedAt(now);
        photo.setProcessingStatus("PROCESSING");
        photo.setFileHash(hash);

        if (tagIds != null && !tagIds.isEmpty()) {
            photo.setTags(new HashSet<>(tagRepo.findAllById(tagIds)));
        }
        if (categoryId != null) {
            photo.setCategory(catRepo.findById(categoryId).orElse(null));
        }

        Photo saved = repo.save(photo);

        // 异步处理：确保主事务提交后再执行，避免异步线程读不到数据
        final Long photoId = saved.getId();
        final String wm = watermark;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    processingSender.send(photoId, target, dateDir, baseName, wm);
                }
            });
        } else {
            processingSender.send(photoId, target, dateDir, baseName, wm);
        }

        uploadCounter.increment();
        uploadBytesCounter.increment(file.getSize());

        return saved;
    }

    @CacheEvict(value = {"photos", "timeline", "map"}, allEntries = true)
    @Transactional
    public PhotoResponse update(Long id, PhotoUpdateRequest req) {
        Photo photo = getById(id);
        photo.setName(req.getName());
        photo.setDescription(req.getDescription());
        if (req.getTagIds() != null) {
            photo.setTags(new HashSet<>(tagRepo.findAllById(req.getTagIds())));
        }
        photo.setCategory(req.getCategoryId() != null
                ? catRepo.findById(req.getCategoryId()).orElse(null) : null);
        if (req.getAlbumIds() != null) {
            albumService.syncPhotoAlbums(photo, req.getAlbumIds());
        }
        return toResponse(repo.save(photo));
    }

    // === 文件路径 ===

    public Path getFilePath(Long id) {
        Photo photo = getByIdIncludeDeleted(id);
        return storage.resolveSafe(photo.getFileName());
    }

    public Path getThumbnailPath(Long id) {
        return getThumbnailPath(id, 400);
    }

    public Path getThumbnailPath(Long id, int width) {
        Photo photo = getByIdIncludeDeleted(id);
        String fn = photo.getFileName();
        int lastSlash = fn.lastIndexOf('/');
        String dateDir = fn.substring(0, lastSlash);
        String baseName = fn.substring(lastSlash + 1);

        Path uploadDir = storage.getUploadDir();
        Path thumbDir = width == 400
                ? uploadDir.resolve(dateDir).resolve("thumbnails")
                : uploadDir.resolve(dateDir).resolve("thumbnails").resolve(String.valueOf(width));
        Path thumb = storage.resolveSafe(uploadDir.relativize(thumbDir.resolve(baseName)).toString());
        if (Files.exists(thumb)) return thumb;

        if (width != 400) {
            Path fallback = storage.resolveSafe(dateDir + "/thumbnails/" + baseName);
            if (Files.exists(fallback)) return fallback;
        }

        return storage.resolveSafe(fn);
    }

    public Path getWebpPath(Long id) {
        Photo photo = getByIdIncludeDeleted(id);
        String fn = photo.getFileName();
        int lastSlash = fn.lastIndexOf('/');
        String dateDir = fn.substring(0, lastSlash);
        String baseName = fn.substring(lastSlash + 1);
        Path webp = storage.resolveSafe(dateDir + "/webp/" + baseName + ".webp");
        if (Files.exists(webp)) return webp;
        return getFilePath(id);
    }

    // === 删除（软删除） ===

    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map"}, allEntries = true)
    public void delete(Long id) {
        Photo photo = getById(id);
        photo.setDeletedAt(LocalDateTime.now());
        photo.setFileHash(null);
        repo.save(photo);
    }

    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map"}, allEntries = true)
    public int batchDelete(List<Long> ids) {
        List<Photo> photos = repo.findAllById(ids);
        if (photos.isEmpty()) return 0;
        for (Photo p : photos) {
            p.setDeletedAt(LocalDateTime.now());
            p.setFileHash(null);
        }
        repo.saveAll(photos);
        return photos.size();
    }

    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map"}, allEntries = true)
    public void restore(Long id) {
        Photo photo = repo.findDeletedById(id)
                .orElseThrow(() -> new BusinessException(404, "未找到可恢复的照片"));
        photo.setDeletedAt(null);
        repo.save(photo);
    }

    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    @Transactional
    public void cleanupDeletedPermanently() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        List<Photo> expired = repo.findDeletedBefore(threshold);
        if (expired.isEmpty()) return;
        for (Photo p : expired) {
            deletePhotoFiles(p);
            exifRepo.findByPhoto_Id(p.getId()).ifPresent(exifRepo::delete);
            repo.delete(p);
        }
        log.info("已永久清理 {} 张过期照片", expired.size());
    }

    public Page<Photo> listDeleted(Pageable pageable) {
        return repo.findDeleted(pageable);
    }

    @Transactional
    public void permanentlyDelete(Long id) {
        Photo photo = repo.findDeletedById(id)
                .orElseThrow(() -> new BusinessException(404, "未找到该照片"));
        exifRepo.findByPhoto_Id(id).ifPresent(exifRepo::delete);
        deletePhotoFiles(photo);
        repo.delete(photo);
    }

    private void deletePhotoFiles(Photo photo) {
        storage.deleteFile(photo.getFileName());
        String fn = photo.getFileName();
        int lastSlash = fn.lastIndexOf('/');
        String dateDir = fn.substring(0, lastSlash);
        String baseName = fn.substring(lastSlash + 1);
        storage.deleteFile(dateDir + "/thumbnails/" + baseName);
        storage.deleteFile(dateDir + "/thumbnails/200/" + baseName);
        storage.deleteFile(dateDir + "/webp/" + baseName + ".webp");
    }

    // === 异步处理重试与恢复 ===

    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map"}, allEntries = true)
    public void retryProcessing(Long id) {
        Photo photo = getById(id);
        photo.setProcessingStatus("PROCESSING");
        photo.setErrorMessage(null);
        repo.save(photo);

        Path target = storage.getUploadDir().resolve(photo.getFileName());
        String fn = photo.getFileName();
        int lastSlash = fn.lastIndexOf('/');
        String dateDir = fn.substring(0, lastSlash);
        String baseName = fn.substring(lastSlash + 1);

        final Long photoId = id;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                processingSender.send(photoId, target, dateDir, baseName, null);
            }
        });
    }

    @PostConstruct
    @Transactional
    public void recoverStuckOnStartup() {
        List<Photo> stuck = repo.findByProcessingStatus("PROCESSING");
        if (stuck.isEmpty()) return;
        int sent = 0;
        for (Photo p : stuck) {
            try {
                Path target = storage.getUploadDir().resolve(p.getFileName());
                String fn = p.getFileName();
                int lastSlash = fn.lastIndexOf('/');
                String dateDir = fn.substring(0, lastSlash);
                String baseName = fn.substring(lastSlash + 1);
                processingSender.send(p.getId(), target, dateDir, baseName, null);
                sent++;
            } catch (Exception e) {
                log.error("启动恢复失败 photo={}: {}", p.getId(), e.getMessage());
                p.setProcessingStatus("FAILED");
                p.setErrorMessage("启动恢复失败: " + e.getMessage());
                repo.save(p);
            }
        }
        if (sent > 0) {
            log.info("已重新发送 {} 张处理中的照片", sent);
        }
    }

    // === 批量上传 ===
    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map"}, allEntries = true)
    public List<Photo> batchUpload(List<MultipartFile> files, String name, String description,
                                    List<Long> tagIds, Long categoryId, String watermark) throws IOException {
        List<Photo> results = new ArrayList<>();
        int skipped = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            try {
                results.add(upload(file, name, description, tagIds, categoryId, watermark));
            } catch (DuplicateException e) {
                skipped++;
            }
        }
        if (skipped > 0) {
            log.info("批量上传跳过 {} 张重复照片", skipped);
        }
        return results;
    }

    // === 迁移 ===

    private static final int BATCH_SIZE = 100;

    public int migrateThumbnails() {
        Path uploadDir = storage.getUploadDir();
        int count = 0;
        var pageable = PageRequest.of(0, BATCH_SIZE);
        Page<Photo> page;
        do {
            page = repo.findAll(pageable);
            for (Photo p : page.getContent()) {
                String fn = p.getFileName();
                int lastSlash = fn.lastIndexOf('/');
                String dateDir = fn.substring(0, lastSlash);
                String baseName = fn.substring(lastSlash + 1);
                Path thumb = uploadDir.resolve(dateDir).resolve("thumbnails").resolve(baseName);
                if (Files.exists(thumb)) continue;
                Path original = uploadDir.resolve(fn);
                if (!Files.exists(original)) continue;
                try {
                    imageService.generateThumbnail(original, dateDir, baseName);
                    if (Files.exists(thumb)) count++;
                } catch (IOException ignored) {}
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
                int lastSlash = fn.lastIndexOf('/');
                String dateDir = fn.substring(0, lastSlash);
                String baseName = fn.substring(lastSlash + 1);
                Path webp = uploadDir.resolve(dateDir).resolve("webp").resolve(baseName + ".webp");
                if (Files.exists(webp)) continue;
                Path original = uploadDir.resolve(fn);
                if (!Files.exists(original)) continue;
                imageService.generateWebp(original, dateDir, baseName);
                if (Files.exists(webp)) count++;
            }
            pageable = pageable.next();
        } while (page.hasNext());
        return count;
    }

    // === EXIF ===

    @Cacheable(value = "timeline", key = "{#sortOrder, #pageable}")
    public Page<TimelineItem> getTimeline(String sortOrder, Pageable pageable) {
        Page<ExifData> page = "asc".equalsIgnoreCase(sortOrder)
                ? exifRepo.findWithDateTakenAndPhotoAsc(pageable)
                : exifRepo.findWithDateTakenAndPhotoDesc(pageable);
        return page.map(this::toTimelineItem);
    }

    @Cacheable(value = "map", key = "{#swLat, #swLng, #neLat, #neLng}")
    public List<MapItem> getMapPhotos(double swLat, double swLng, double neLat, double neLng) {
        List<ExifData> list = exifRepo.findWithGpsInBounds(swLat, swLng, neLat, neLng,
                PageRequest.of(0, 500));
        return list.stream().map(e -> {
            MapItem item = MapItem.from(e);
            double[] gcj = CoordUtil.wgs84ToGcj02(e.getLongitude(), e.getLatitude());
            item.setLatitude(gcj[1]);
            item.setLongitude(gcj[0]);
            return item;
        }).toList();
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

    public ExifData extractExifForPhoto(Long id) {
        Photo photo = getById(id);
        Path filePath = storage.getUploadDir().resolve(photo.getFileName());
        if (!Files.exists(filePath)) return null;
        return exifService.extractAndSave(photo, filePath);
    }

    // === 变换 ===

    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map"}, allEntries = true)
    public void transformPhoto(Long id, int rotate, String mirror, Double cx, Double cy, Double cw, Double ch) throws IOException {
        Photo photo = getById(id);
        Path filePath = storage.getUploadDir().resolve(photo.getFileName());
        if (!Files.exists(filePath)) return;

        BufferedImage img = ImageIO.read(filePath.toFile());
        if (img == null) throw new IOException("无法读取图片");

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
        ImageIO.write(img, format, filePath.toFile());
        photo.setFileSize(Files.size(filePath));

        String fn = photo.getFileName();
        int lastSlash = fn.lastIndexOf('/');
        String dateDir = fn.substring(0, lastSlash);
        String baseName = fn.substring(lastSlash + 1);
        imageService.generateThumbnail(filePath, dateDir, baseName);
        imageService.generateThumbnail(filePath, dateDir, baseName, 200);
        imageService.generateWebp(filePath, dateDir, baseName);
        repo.save(photo);

        try {
            exifService.extractAndSave(photo, filePath);
        } catch (Exception ignored) {}
    }

    // === 哈希 ===

    public static String computeSha256(MultipartFile file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            try (var in = file.getInputStream()) {
                while ((n = in.read(buf)) != -1) {
                    md.update(buf, 0, n);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 不可用", e);
        }
    }

    // === DTO 转换 ===

    private void eagerLoad(Photo photo) {
        if (photo.getTags() != null) photo.getTags().size();
        if (photo.getAlbums() != null) photo.getAlbums().size();
        if (photo.getExifData() != null) photo.getExifData().getCameraModel();
    }

    public PhotoResponse toResponse(Photo photo) {
        return PhotoResponse.from(photo);
    }

    public TimelineItem toTimelineItem(ExifData exif) {
        return TimelineItem.from(exif);
    }

    public MapItem toMapItem(ExifData exif) {
        return MapItem.from(exif);
    }
}
