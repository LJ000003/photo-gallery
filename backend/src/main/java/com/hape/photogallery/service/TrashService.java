package com.hape.photogallery.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.PhotoRepository;

/**
 * 回收站服务（从 PhotoService 拆出）：软删除照片的列表/恢复/彻底删除/30 天定时清理。
 * @Scheduled 定时注解随方法迁移，清理频率与拆出前一致（每日 3:00 Asia/Shanghai，
 * 独立 Bean 保证跨类调用走 @Transactional 代理——与 BackupScheduler 同法）。
 * <p>
 * purge 原子性（P0 修复）：每张照片独立 REQUIRES_NEW 事务 + native 条件删除
 * （hardDeleteIfStillDeleted）——单张失败/崩溃回滚损失限定到该照片；期间被恢复的
 * 照片条件删除返回 0 行 → 跳过文件删除（防「恢复竞态连坐硬删」）。顺序先删行后删文件：
 * 崩溃只留孤儿文件（无用户影响），不会产生「行在文件无」的幽灵记录。
 * exif_data 行由 V3 FK ON DELETE CASCADE 级联，无需手动删除。
 */
@Service
public class TrashService {

    private static final Logger log = LoggerFactory.getLogger(TrashService.class);

    private final PhotoRepository repo;
    private final FilePathResolver filePathResolver;
    private final AlbumService albumService;
    private final PhotoQueryService photoQueryService;
    private final TransactionTemplate purgeTx;

    public TrashService(PhotoRepository repo,
                        FilePathResolver filePathResolver, AlbumService albumService,
                        PhotoQueryService photoQueryService,
                        PlatformTransactionManager txManager) {
        this.repo = repo;
        this.filePathResolver = filePathResolver;
        this.albumService = albumService;
        this.photoQueryService = photoQueryService;
        this.purgeTx = new TransactionTemplate(txManager);
        this.purgeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    // 曾缺 "albums"：restore 改变 countByAlbum（@SQLRestriction 下软删照片不计入 photoCount），
    // 而 AlbumService.listAll 是 @Cacheable("albums")——不清则相册计数最长 30s 过期
    @CacheEvict(value = {"photos", "timeline", "map", "stats", "albums"}, allEntries = true)
    public void restore(Long id) {
        Photo photo = repo.findDeletedById(id)
                .orElseThrow(() -> new BusinessException(404, "未找到可恢复的照片"));
        photo.setDeletedAt(null);
        backfillFileHash(photo);
        repo.save(photo);
    }

    /**
     * 恢复时回填 fileHash（delete 清空它释放唯一索引，恢复不回填则去重对该照片永久失效）。
     * 软删期间重传的同文件可能已作为新照片入库（file_hash 唯一索引，V8），回填前查重：
     * 已占用则留空（不破坏既有去重）；文件缺失/哈希失败留空——恢复流程永不因哈希失败中断。
     * 查重需按 id 排除自身：Hibernate AUTO flush 已把 deletedAt 写回，findWithDetailsByFileHash
     * 可能命中正在恢复的这张照片。
     */
    private void backfillFileHash(Photo photo) {
        try {
            Path original = filePathResolver.getFilePath(photo.getId());
            if (original == null || !Files.exists(original)) {
                log.warn("恢复照片原文件缺失，fileHash 留空 photo={}", photo.getId());
                return;
            }
            String hash = PhotoService.computeSha256(original);
            boolean occupied = repo.findWithDetailsByFileHash(hash)
                    .filter(existing -> !existing.getId().equals(photo.getId()))
                    .isPresent();
            if (!occupied) {
                photo.setFileHash(hash);
            } else {
                log.info("恢复照片 fileHash 已被其他照片占用，留空 photo={}", photo.getId());
            }
        } catch (IOException e) {
            log.warn("恢复照片计算 fileHash 失败，留空 photo={}: {}", photo.getId(), e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    public void cleanupDeletedPermanently() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        List<Photo> expired = repo.findDeletedBefore(threshold);
        if (expired.isEmpty()) return;
        int deleted = 0;
        for (Photo p : expired) {
            Boolean rowDeleted = purgeTx.execute(status -> {
                if (repo.hardDeleteIfStillDeleted(p.getId()) == 0) return false;
                filePathResolver.deletePhotoFiles(p);
                // 以其为封面的相册重选（照片已软删 30 天，重选从剩余未删照片中取第一张）
                albumService.reselectCoversAfterPhotoDeletion(p.getId());
                return true;
            });
            if (Boolean.TRUE.equals(rowDeleted)) deleted++;
        }
        log.info("已永久清理 {} 张过期照片", deleted);
    }

    public Page<Photo> listDeleted(Pageable pageable) {
        return repo.findDeleted(pageable);
    }

    /** 回收站列表（事务内 map toResponse——懒加载代理序列化防护，曾实测 500） */
    @Transactional(readOnly = true)
    public Page<com.hape.photogallery.dto.PhotoResponse> listDeletedResponses(Pageable pageable) {
        return listDeleted(pageable).map(photoQueryService::toResponse);
    }

    @Transactional
    // 曾缺 evict：彻底删除后照片仍在 photos 列表缓存残留 30s → 前端出现点击即 404 的假条目
    @CacheEvict(value = {"photos", "timeline", "map", "stats"}, allEntries = true)
    public void permanentlyDelete(Long id) {
        Photo photo = repo.findDeletedById(id)
                .orElseThrow(() -> new BusinessException(404, "未找到该照片"));
        // 条件删除：期间被恢复则 0 行 → 中止（不删已恢复照片的磁盘文件）
        if (repo.hardDeleteIfStillDeleted(id) == 0) {
            throw new BusinessException(404, "该照片已恢复，无法彻底删除");
        }
        filePathResolver.deletePhotoFiles(photo);
        albumService.reselectCoversAfterPhotoDeletion(id);
    }
}
