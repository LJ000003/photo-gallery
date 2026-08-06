package com.hape.photogallery.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

import com.hape.photogallery.dto.BatchPhotoUpdateRequest;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.PhotoUpdateRequest;
import com.hape.photogallery.dto.UploadParams;
import com.hape.photogallery.entity.Category;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.ProcessingStatus;
import com.hape.photogallery.entity.Tag;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.exception.DuplicateException;
import com.hape.photogallery.exception.FileSizeExceededException;
import com.hape.photogallery.messaging.ProcessingMessageSender;
import com.hape.photogallery.repository.CategoryRepository;
import com.hape.photogallery.repository.PhotoRepository;
import com.hape.photogallery.repository.TagRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;

/**
 * 照片写路径核心服务（P2-#15 职责边界重构）：
 * 上传/更新/删除/批量/处理恢复。查询侧（列表/搜索/DTO 转换/回收站）已拆至
 * PhotoQueryService 与 TrashService——本服务单向依赖 PhotoQueryService
 * （getById/toResponse 走跨 bean 代理，事务/缓存注解生效），避免互相注入的循环依赖。
 * 自调用（batchUpload→upload、upload→uploadInTx private）仍在，靠事务模板/外层
 * @CacheEvict 兜底，行为不变。
 */
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
    private final ImageProcessingService imageService;
    private final AlbumService albumService;
    private final StorageService storage;
    private final ProcessingMessageSender processingSender;
    private final TransactionTemplate transactionTemplate;
    private final FilePathResolver filePathResolver;
    private final PhotoQueryService photoQueryService;

    public PhotoService(PhotoRepository repo, TagRepository tagRepo, CategoryRepository catRepo,
                        ImageProcessingService imageService,
                        AlbumService albumService,
                        StorageService storage,
                        ProcessingMessageSender processingSender,
                        TransactionTemplate transactionTemplate,
                        FilePathResolver filePathResolver,
                        PhotoQueryService photoQueryService) {
        this.repo = repo;
        this.tagRepo = tagRepo;
        this.catRepo = catRepo;
        this.imageService = imageService;
        this.albumService = albumService;
        this.storage = storage;
        this.processingSender = processingSender;
        this.transactionTemplate = transactionTemplate;
        this.filePathResolver = filePathResolver;
        this.photoQueryService = photoQueryService;
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

    /**
     * 上传单张照片（P4-#41①/#46 事务边界重构）：
     *  - 魔数校验 + SHA-256 在事务外计算，不持数据库连接做秒级哈希/读文件；
     *  - transactionTemplate 只包 DB 段（查重 → 落盘 → 保存）；落盘/save 失败删除已写文件再抛出，不留孤儿文件；
     *  - execute 返回即已提交，随后发送异步处理消息（若存在外层事务则回退 afterCommit 注册，语义不变）。
     */
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    public Photo upload(MultipartFile file, UploadParams params) throws IOException {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileSizeExceededException("文件过大，请上传小于 10MB 的图片");
        }
        try (var magicIn = file.getInputStream()) {
            imageService.validateImageMagicBytes(magicIn);
        }
        String hash = computeSha256(file);

        LocalDateTime now = LocalDateTime.now();
        String dateDir = String.format("%04d/%02d", now.getYear(), now.getMonthValue());
        // 原始文件名先消毒再入库：`../`、路径分隔符、控制字符一律替换，杜绝写路径穿越
        String baseName = UUID.randomUUID() + "_" + sanitizeFileName(file.getOriginalFilename());
        String storedName = dateDir + "/" + baseName;
        // 写路径复用读路径的 resolveSafe 语义（normalize + startsWith(uploadDir)）
        Path target = storage.resolveSafe(storedName);

        final Photo saved;
        try {
            saved = transactionTemplate.execute(status -> {
                try {
                    return uploadInTx(file, params, hash, dateDir, storedName, target, now);
                } catch (IOException e) {
                    // TransactionCallback 不支持 checked 异常，包装后在外层还原
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } catch (DataIntegrityViolationException e) {
            // 并发同 hash 上传：check-then-insert 竞态撞唯一索引 → 删残留文件后重查，
            // 找到则返回 DuplicateException 语义（携带已有照片），否则原样抛出（P4-#42）
            deleteStoredSafely(storedName);
            Photo existing = repo.findWithDetailsByFileHash(hash).orElse(null);
            if (existing != null) {
                throw new DuplicateException(photoQueryService.toResponse(existing));
            }
            throw e;
        }
        if (saved == null) {
            // execute 声明可返回 null（防御：回调必返回或抛异常，理论不可达）
            throw new IllegalStateException("上传事务未返回结果");
        }

        // 事务已提交：发送异步处理消息（photoId 已落库，处理器可读）
        final Long photoId = saved.getId();
        final String wm = params.watermark();
        Runnable send = () -> {
            try {
                processingSender.send(photoId, target, dateDir, baseName, wm);
            } catch (Exception e) {
                // Rabbit 挂/队列满等：不把 500 抛给请求（DB 已提交、缓存已清），
                // 靠 5 分钟定时重扫兜底恢复（P4-#41②）
                log.warn("处理消息发送失败 photo={}: {}", photoId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new SendAfterCommit(send));
        } else {
            send.run();
        }

        uploadCounter.increment();
        uploadBytesCounter.increment(file.getSize());

        return saved;
    }

    /** 事务内执行的上传主体：查重 → 建目录 → 落盘 → 保存。任何失败删除已写文件后抛出（防孤儿文件）。 */
    private Photo uploadInTx(MultipartFile file, UploadParams p, String hash,
                             String dateDir, String storedName, Path target, LocalDateTime now) throws IOException {
        repo.findWithDetailsByFileHash(hash).ifPresent(existing -> {
            throw new DuplicateException(photoQueryService.toResponse(existing));
        });

        storage.createDirectories(storage.getUploadDir().resolve(dateDir));
        try {
            storage.store(file, target);

            Photo photo = new Photo();
            photo.setName(p.name() != null && !p.name().isBlank() ? p.name() : file.getOriginalFilename());
            photo.setDescription(p.description());
            photo.setFileName(storedName);
            photo.setOriginalFileName(file.getOriginalFilename());
            photo.setFileSize(file.getSize());
            photo.setContentType(file.getContentType());
            photo.setCreatedAt(now);
            photo.setProcessingStatus(ProcessingStatus.PROCESSING);
            photo.setFileHash(hash);

            if (p.tagIds() != null && !p.tagIds().isEmpty()) {
                photo.setTags(new HashSet<>(tagRepo.findAllById(p.tagIds())));
            }
            if (p.categoryId() != null) {
                photo.setCategory(catRepo.findById(p.categoryId()).orElse(null));
            }
            return repo.save(photo);
        } catch (RuntimeException | IOException e) {
            // save 失败（含唯一索引竞态）/落盘失败：删除刚写的文件再抛出
            deleteStoredSafely(storedName);
            throw e;
        }
    }

    /** 删除上传残留文件（不存在则忽略） */
    private void deleteStoredSafely(String storedName) {
        try {
            storage.deleteFile(storedName);
        } catch (Exception cleanup) {
            log.warn("清理上传残留文件失败: {}", cleanup.getMessage());
        }
    }

    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    @Transactional
    public PhotoResponse update(Long id, PhotoUpdateRequest req) {
        Photo photo = photoQueryService.getById(id);
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
        return photoQueryService.toResponse(repo.save(photo));
    }

    // === 文件路径 ===

    // 路径解析/产物路径已拆至 FilePathResolver（P4-#37）：PhotoController 直调 resolver，
    // PhotoService 内部用 resolver.parseFilePath/deletePhotoFiles。

    // === 删除（软删除） ===

    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    public void delete(Long id) {
        Photo photo = photoQueryService.getById(id);
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
        return photos.stream().map(photoQueryService::toResponse).toList();
    }

    // === 异步处理重试与恢复 ===

    @Transactional
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    public void retryProcessing(Long id) {
        Photo photo = photoQueryService.getById(id);
        photo.setProcessingStatus(ProcessingStatus.PROCESSING);
        photo.setErrorMessage(null);
        repo.save(photo);

        Path target = storage.getUploadDir().resolve(photo.getFileName());
        FilePathResolver.FilePathParts parts = filePathResolver.parseFilePath(photo.getFileName());

        final Long photoId = id;
        final Path sendTarget = target;
        final FilePathResolver.FilePathParts sendParts = parts;
        TransactionSynchronizationManager.registerSynchronization(new SendAfterCommit(() ->
                processingSender.send(photoId, sendTarget, sendParts.dateDir(), sendParts.baseName(), null)));
    }

    /** 启动时立即恢复一次 + 每 5 分钟兜底重扫（P4-#41②）：DiscardPolicy 丢弃的处理消息在此补发 */
    @PostConstruct
    public void recoverStuckOnStartup() {
        recoverStuckProcessing();
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void recoverStuckProcessingScheduled() {
        recoverStuckProcessing();
    }

    /** 扫描 PROCESSING 照片重新发送处理消息（幂等：处理链可重复执行） */
    void recoverStuckProcessing() {
        List<Photo> stuck = transactionTemplate.execute(status -> {
            List<Photo> result = repo.findByProcessingStatus(ProcessingStatus.PROCESSING);
            // 事务内读取标量字段（fileName），避免事务外懒加载（赋值消费返回值，spotbugs RV 干净）
            for (Photo p : result) {
                String fileName = p.getFileName();
                if (fileName == null) {
                    log.warn("PROCESSING 照片缺少文件路径 photo={}", p.getId());
                }
            }
            return result;
        });
        if (stuck == null || stuck.isEmpty()) return;
        int sent = 0;
        for (Photo p : stuck) {
            try {
                Path target = storage.getUploadDir().resolve(p.getFileName());
                FilePathResolver.FilePathParts parts = filePathResolver.parseFilePath(p.getFileName());
                processingSender.send(p.getId(), target, parts.dateDir(), parts.baseName(), null);
                sent++;
            } catch (Exception e) {
                log.error("启动恢复失败 photo={}: {}", p.getId(), e.getMessage());
                final Long photoId = p.getId();
                final String errMsg = e.getMessage();
                transactionTemplate.execute(status -> {
                    Photo photo = repo.findById(photoId).orElse(null);
                    if (photo != null) {
                        photo.setProcessingStatus(ProcessingStatus.FAILED);
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
    /**
     * 批量上传（P4-#46）：逐文件独立事务——单张失败/重复只跳过自己，不拖垮整批；
     * 不再有整批 @Transactional（否则一个坏文件回滚 50 张且磁盘留孤儿文件）。
     * 重复照片计数跳过，其余异常 log 原因后继续。响应仍是成功列表（形状不变）。
     */
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    public List<Photo> batchUpload(List<MultipartFile> files, UploadParams params) throws IOException {
        List<Photo> results = new ArrayList<>();
        int skipped = 0;
        int failed = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            try {
                results.add(upload(file, params));
            } catch (DuplicateException e) {
                skipped++;
            } catch (Exception e) {
                failed++;
                log.warn("批量上传跳过文件 {}: {}", file.getOriginalFilename(), e.getMessage());
            }
        }
        if (skipped > 0) {
            log.info("批量上传跳过 {} 张重复照片", skipped);
        }
        if (failed > 0) {
            log.warn("批量上传失败 {} 张（已跳过，不影响其余文件）", failed);
        }
        return results;
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

    /** 事务提交后执行发送回调的同步器（静态内部类——避免 SpotBugs SIC_INNER_SHOULD_BE_STATIC_ANON） */
    private static final class SendAfterCommit implements TransactionSynchronization {
        private final Runnable send;

        SendAfterCommit(Runnable send) {
            this.send = send;
        }

        @Override
        public void afterCommit() {
            send.run();
        }
    }
}
