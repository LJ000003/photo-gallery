package com.hape.photogallery.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
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
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

import com.hape.photogallery.config.MediaSignatureService;
import com.hape.photogallery.dto.BatchPhotoUpdateRequest;
import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.PhotoUpdateRequest;
import com.hape.photogallery.dto.TimelineItem;
import com.hape.photogallery.entity.Category;
import com.hape.photogallery.entity.ExifData;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.Tag;
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
    private final TransactionTemplate transactionTemplate;
    private final MediaSignatureService mediaSignature;

    public PhotoService(PhotoRepository repo, TagRepository tagRepo, CategoryRepository catRepo,
                        ExifDataRepository exifRepo,
                        ExifService exifService,
                        ImageProcessingService imageService,
                        AlbumService albumService,
                        StorageService storage,
                        ProcessingMessageSender processingSender,
                        TransactionTemplate transactionTemplate,
                        MediaSignatureService mediaSignature) {
        this.repo = repo;
        this.tagRepo = tagRepo;
        this.catRepo = catRepo;
        this.exifRepo = exifRepo;
        this.exifService = exifService;
        this.imageService = imageService;
        this.albumService = albumService;
        this.storage = storage;
        this.processingSender = processingSender;
        this.transactionTemplate = transactionTemplate;
        this.mediaSignature = mediaSignature;
    }

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

    /** 缓存照片列表（DTO 形式，避免 Hibernate 懒加载代理被序列化到 Redis） */
    @Transactional(readOnly = true)
    @Cacheable(value = "photos", key = "{#tagIds, #categoryIds, #pageable}")
    public Page<PhotoResponse> listAllResponses(List<Long> tagIds, List<Long> categoryIds, Pageable pageable) {
        return listAll(tagIds, categoryIds, pageable).map(this::toResponse);
    }

    /**
     * 搜索（含标签/分类组合过滤）。
     * native query 的排序必须是数据库列名（Hibernate 不做属性→列名翻译），
     * 而前端传的是实体属性名（createdAt/fileSize），需在此映射，否则 MySQL 报 Unknown column。
     */
    public Page<Photo> search(String q, List<Long> tagIds, List<Long> categoryIds, Pageable pageable) {
        if (q == null || q.isBlank()) return repo.findAll(pageable);
        Pageable columnSort = toColumnSort(pageable);
        // 先剥离 FULLTEXT BOOLEAN MODE 运算符再判长度：`ab"` → `ab`，`a"` → `a`（应走 LIKE）
        String query = sanitizeFullText(q);
        if (query.isEmpty()) return Page.empty(pageable);
        boolean hasTags = tagIds != null && !tagIds.isEmpty();
        boolean hasCats = categoryIds != null && !categoryIds.isEmpty();
        // 单字（<2 字符）：FULLTEXT 的 ngram 双字分词无法命中，fallback 到 LIKE 子串匹配
        if (query.length() < 2) {
            String pattern = "%" + escapeLike(query) + "%";
            if (hasTags && hasCats) return repo.searchByLikeWithTagAndCategoryIds(pattern, tagIds, categoryIds, columnSort);
            if (hasTags) return repo.searchByLikeWithTagIds(pattern, tagIds, columnSort);
            if (hasCats) return repo.searchByLikeWithCategoryIds(pattern, categoryIds, columnSort);
            return repo.searchByLike(pattern, columnSort);
        }
        if (hasTags && hasCats) return repo.searchWithTagAndCategoryIds(query, tagIds, categoryIds, columnSort);
        if (hasTags) return repo.searchWithTagIds(query, tagIds, columnSort);
        if (hasCats) return repo.searchWithCategoryIds(query, categoryIds, columnSort);
        return repo.search(query, columnSort);
    }

    /** LIKE 通配符转义（MySQL 默认转义字符为反斜杠） */
    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** FULLTEXT BOOLEAN MODE 运算符：MySQL 将其按查询表达式解析（`-x` 排除、`"` 短语），
     *  参数绑定防不了语法错误（`ab"` → 1210 → 500）与语义劫持，只能从输入中剥离 */
    private static final Pattern BOOLEAN_OPERATORS = Pattern.compile("[+\\-<>()~*\"@]");

    /** 剥离 BOOLEAN MODE 运算符并折叠空白：用户输入一律按纯关键词处理 */
    static String sanitizeFullText(String q) {
        String cleaned = BOOLEAN_OPERATORS.matcher(q).replaceAll(" ");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    /** 上传文件名消毒：路径分隔符/控制字符替换为下划线，连续点号折叠，
     *  最后按安全字符白名单（字母/数字/点/下划线/连字符/空格，含中文）兜底，并截断超长文件名。 */
    static String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) return "";
        String cleaned = originalFilename
                .replaceAll("[\\\\/\\u0000-\\u001F]", "_")
                .replaceAll("\\.{2,}", ".");
        cleaned = cleaned.replaceAll("[^\\p{L}\\p{N}._\\- ]", "_");
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }

    /** 实体属性名 → 数据库列名（native query 排序用）。
     *  白名单之外的属性一律 400，杜绝 ORDER BY 字符串拼接注入（前端仅用 createdAt/name/fileSize） */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "created_at",
            "fileSize", "file_size",
            "name", "name"
    );

    private Pageable toColumnSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) return pageable;
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            String column = SORT_COLUMNS.get(order.getProperty());
            if (column == null) {
                throw new BusinessException(400, "不支持的排序字段: " + order.getProperty());
            }
            orders.add(new Sort.Order(order.getDirection(), column));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
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
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    public Photo upload(MultipartFile file, String name, String description,
                        List<Long> tagIds, Long categoryId, String watermark) throws IOException {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileSizeExceededException("文件过大，请上传小于 10MB 的图片");
        }
        try (var magicIn = file.getInputStream()) {
            imageService.validateImageMagicBytes(magicIn);
        }

        String hash = computeSha256(file);
        repo.findByFileHash(hash).ifPresent(existing -> {
            eagerLoad(existing);
            throw new DuplicateException(toResponse(existing));
        });

        LocalDateTime now = LocalDateTime.now();
        String dateDir = String.format("%04d/%02d", now.getYear(), now.getMonthValue());
        Path datePath = storage.getUploadDir().resolve(dateDir);
        storage.createDirectories(datePath);

        // 原始文件名先消毒再入库：`../`、路径分隔符、控制字符一律替换，杜绝写路径穿越
        String baseName = UUID.randomUUID() + "_" + sanitizeFileName(file.getOriginalFilename());
        String storedName = dateDir + "/" + baseName;
        // 写路径复用读路径的 resolveSafe 语义（normalize + startsWith(uploadDir)）
        Path target = storage.resolveSafe(storedName);
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

    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    @Transactional
    public PhotoResponse update(Long id, PhotoUpdateRequest req) {
        Photo photo = getById(id);
        photo.setName(req.getName());
        photo.setDescription(req.getDescription());
        if (req.getTagIds() != null) {
            photo.setTags(new HashSet<>(tagRepo.findAllById(req.getTagIds())));
        }
        // categoryId 语义与 albumId=0 的"未分配"约定一致：
        //   null = 不修改分类；0 = 清除分类；>0 = 设为指定分类（不存在则 404，不做静默置 null）
        Long catId = req.getCategoryId();
        if (catId != null) {
            if (catId == 0L) {
                photo.setCategory(null);
            } else {
                photo.setCategory(catRepo.findById(catId)
                        .orElseThrow(() -> new BusinessException(404, "分类不存在")));
            }
        }
        if (req.getAlbumIds() != null) {
            albumService.syncPhotoAlbums(photo, req.getAlbumIds());
        }
        return toResponse(repo.save(photo));
    }

    // === 文件路径 ===

    /**
     * 将存储路径（如 "2024/01/uuid_file.jpg"）解析为 {dateDir, baseName}。
     * 对不含 '/' 的文件名安全降级，防止 StringIndexOutOfBoundsException。
     */
    private record FilePathParts(String dateDir, String baseName) {}
    private FilePathParts parseFilePath(String fn) {
        int lastSlash = fn.lastIndexOf('/');
        if (lastSlash < 0) {
            return new FilePathParts("", fn);
        }
        return new FilePathParts(fn.substring(0, lastSlash), fn.substring(lastSlash + 1));
    }

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
        FilePathParts parts = parseFilePath(fn);

        Path uploadDir = storage.getUploadDir();
        Path thumbDir = width == 400
                ? uploadDir.resolve(parts.dateDir).resolve("thumbnails")
                : uploadDir.resolve(parts.dateDir).resolve("thumbnails").resolve(String.valueOf(width));
        Path thumb = storage.resolveSafe(uploadDir.relativize(thumbDir.resolve(parts.baseName)).toString());
        if (Files.exists(thumb)) return thumb;

        if (width != 400) {
            Path fallback = storage.resolveSafe(parts.dateDir + "/thumbnails/" + parts.baseName);
            if (Files.exists(fallback)) return fallback;
        }

        return storage.resolveSafe(fn);
    }

    public Path getWebpPath(Long id) {
        Photo photo = getByIdIncludeDeleted(id);
        String fn = photo.getFileName();
        FilePathParts parts = parseFilePath(fn);
        Path webp = storage.resolveSafe(parts.dateDir + "/webp/" + parts.baseName + ".webp");
        if (Files.exists(webp)) return webp;
        return getFilePath(id);
    }

    // === 删除（软删除） ===

    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    public void delete(Long id) {
        Photo photo = getById(id);
        photo.setDeletedAt(LocalDateTime.now());
        photo.setFileHash(null);
        repo.save(photo);
    }

    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
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

    // === 批量编辑 ===

    /**
     * 批量编辑元数据：标签/相册按「添加/移除」列表操作（移除先于添加，重叠时添加胜出），
     * 分类按 NONE/SET/CLEAR 三态处理。SET 时分类不存在则 404 并整体回滚，
     * 绝不静默清空（单张 update 的旧行为不复制）。不存在的照片 ID 静默跳过。
     */
    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map", "albums", "stats"}, allEntries = true)
    public List<PhotoResponse> batchUpdate(BatchPhotoUpdateRequest req) {
        List<Photo> photos = repo.findAllById(req.getPhotoIds());
        if (photos.isEmpty()) return List.of();

        // 标签解析一次，所有照片共享
        Set<Tag> tagsToAdd = new HashSet<>(tagRepo.findAllById(req.getAddTagIds()));
        Set<Long> tagIdsToRemove = new HashSet<>(req.getRemoveTagIds());

        // 分类解析一次；SET 时分类不存在则整个批次失败回滚
        Category category = null;
        if (req.getCategoryOp() == BatchPhotoUpdateRequest.CategoryOp.SET) {
            category = catRepo.findById(req.getCategoryId())
                    .orElseThrow(() -> new BusinessException(404, "分类不存在"));
        }

        for (Photo p : photos) {
            if (!tagIdsToRemove.isEmpty()) {
                p.getTags().removeIf(t -> tagIdsToRemove.contains(t.getId()));
            }
            if (!tagsToAdd.isEmpty()) {
                p.getTags().addAll(tagsToAdd);
            }
            if (req.getCategoryOp() == BatchPhotoUpdateRequest.CategoryOp.SET) {
                p.setCategory(category);
            } else if (req.getCategoryOp() == BatchPhotoUpdateRequest.CategoryOp.CLEAR) {
                p.setCategory(null);
            }
        }

        // 相册复用现有 addPhotos/removePhotos（自带封面重选逻辑，REQUIRED 事务并入外层，原子性）
        for (Long aid : req.getAddAlbumIds()) {
            albumService.addPhotos(aid, req.getPhotoIds());
        }
        for (Long aid : req.getRemoveAlbumIds()) {
            albumService.removePhotos(aid, req.getPhotoIds());
        }

        repo.saveAll(photos);
        return photos.stream().map(this::toResponse).toList();
    }

    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
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
        FilePathParts parts = parseFilePath(photo.getFileName());
        storage.deleteFile(parts.dateDir + "/thumbnails/" + parts.baseName);
        storage.deleteFile(parts.dateDir + "/thumbnails/200/" + parts.baseName);
        storage.deleteFile(parts.dateDir + "/webp/" + parts.baseName + ".webp");
    }

    // === 异步处理重试与恢复 ===

    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    public void retryProcessing(Long id) {
        Photo photo = getById(id);
        photo.setProcessingStatus("PROCESSING");
        photo.setErrorMessage(null);
        repo.save(photo);

        Path target = storage.getUploadDir().resolve(photo.getFileName());
        FilePathParts parts = parseFilePath(photo.getFileName());

        final Long photoId = id;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                processingSender.send(photoId, target, parts.dateDir, parts.baseName, null);
            }
        });
    }

    @PostConstruct
    public void recoverStuckOnStartup() {
        List<Photo> stuck = transactionTemplate.execute(status -> {
            List<Photo> result = repo.findByProcessingStatus("PROCESSING");
            // 预先初始化懒加载属性，避免事务外访问
            for (Photo p : result) {
                p.getFileName(); // 触发懒加载
            }
            return result;
        });
        if (stuck == null || stuck.isEmpty()) return;
        int sent = 0;
        for (Photo p : stuck) {
            try {
                Path target = storage.getUploadDir().resolve(p.getFileName());
                FilePathParts parts = parseFilePath(p.getFileName());
                processingSender.send(p.getId(), target, parts.dateDir, parts.baseName, null);
                sent++;
            } catch (Exception e) {
                log.error("启动恢复失败 photo={}: {}", p.getId(), e.getMessage());
                final Long photoId = p.getId();
                final String errMsg = e.getMessage();
                transactionTemplate.execute(status -> {
                    Photo photo = repo.findById(photoId).orElse(null);
                    if (photo != null) {
                        photo.setProcessingStatus("FAILED");
                        photo.setErrorMessage("启动恢复失败: " + errMsg);
                        repo.save(photo);
                    }
                    return null;
                });
            }
        }
        if (sent > 0) {
            log.info("已重新发送 {} 张处理中的照片", sent);
        }
    }

    // === 批量上传 ===
    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
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
                FilePathParts parts = parseFilePath(fn);
                Path thumb = uploadDir.resolve(parts.dateDir).resolve("thumbnails").resolve(parts.baseName);
                if (Files.exists(thumb)) continue;
                Path original = uploadDir.resolve(fn);
                if (!Files.exists(original)) continue;
                try {
                    imageService.generateThumbnail(original, parts.dateDir, parts.baseName);
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
                FilePathParts parts = parseFilePath(fn);
                Path webp = uploadDir.resolve(parts.dateDir).resolve("webp").resolve(parts.baseName + ".webp");
                if (Files.exists(webp)) continue;
                Path original = uploadDir.resolve(fn);
                if (!Files.exists(original)) continue;
                imageService.generateWebp(original, parts.dateDir, parts.baseName);
                if (Files.exists(webp)) count++;
            }
            pageable = pageable.next();
        } while (page.hasNext());
        return count;
    }

    // === EXIF ===

    @Transactional(readOnly = true)
    @Cacheable(value = "timeline", key = "{#sortOrder, #pageable}")
    public Page<TimelineItem> getTimeline(String sortOrder, Pageable pageable) {
        Page<ExifData> page = "asc".equalsIgnoreCase(sortOrder)
                ? exifRepo.findWithDateTakenAndPhotoAsc(pageable)
                : exifRepo.findWithDateTakenAndPhotoDesc(pageable);
        return page.map(this::toTimelineItem);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "map", key = "{#swLat, #swLng, #neLat, #neLng}")
    public List<MapItem> getMapPhotos(double swLat, double swLng, double neLat, double neLng) {
        List<ExifData> list = exifRepo.findWithGpsInBounds(swLat, swLng, neLat, neLng,
                PageRequest.of(0, 500));
        // 必须走 toMapItem（内联 MapItem.from 会漏掉 mediaToken 短时签名，
        // 前端 popup 缩略图无鉴权 401）
        return list.stream().map(e -> {
            MapItem item = toMapItem(e);
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
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    public void transformPhoto(Long id, int rotate, String mirror, Double cx, Double cy, Double cw, Double ch) throws IOException {
        Photo photo = getById(id);
        Path filePath = storage.getUploadDir().resolve(photo.getFileName());
        if (!Files.exists(filePath)) return;

        try {
            doTransformPhoto(photo, filePath, rotate, mirror, cx, cy, cw, ch);
        } catch (IOException e) {
            // 图片无法解码（读取返回 null / IIOException）或写入编码失败（如 JPEG 编码器拒绝
            // 异常色彩空间）——均属"用户图片不可处理"，返回业务错误而非 500
            log.warn("Transform failed for photo {}: {}", id, e.getMessage());
            throw new BusinessException(400, "图片无法处理，可能已损坏");
        }
        repo.save(photo);
    }

    private void doTransformPhoto(Photo photo, Path filePath, int rotate, String mirror,
                                  Double cx, Double cy, Double cw, Double ch) throws IOException {
        BufferedImage img = ImageIO.read(filePath.toFile());
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
        ImageIO.write(img, format, filePath.toFile());
        photo.setFileSize(Files.size(filePath));

        String fn = photo.getFileName();
        FilePathParts parts = parseFilePath(fn);
        imageService.generateThumbnail(filePath, parts.dateDir, parts.baseName);
        imageService.generateThumbnail(filePath, parts.dateDir, parts.baseName, 200);
        imageService.generateWebp(filePath, parts.dateDir, parts.baseName);

        try {
            exifService.extractAndSave(photo, filePath);
        } catch (Exception e) {
            log.warn("变换后 EXIF 提取失败 photo={}: {}", photo.getId(), e.getMessage());
        }
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
        PhotoResponse r = PhotoResponse.from(photo);
        r.setMediaToken(mediaSignature.sign(photo.getId()));
        return r;
    }

    public TimelineItem toTimelineItem(ExifData exif) {
        TimelineItem item = TimelineItem.from(exif);
        item.setMediaToken(mediaSignature.sign(exif.getPhotoId()));
        return item;
    }

    public MapItem toMapItem(ExifData exif) {
        MapItem item = MapItem.from(exif);
        item.setMediaToken(mediaSignature.sign(exif.getPhotoId()));
        return item;
    }
}
