package com.hape.photogallery.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.ExifDataRepository;
import com.hape.photogallery.repository.PhotoRepository;

/**
 * 回收站服务（从 PhotoService 拆出）：软删除照片的列表/恢复/彻底删除/30 天定时清理。
 * @Scheduled 定时注解随方法迁移，清理频率与拆出前一致（每日 3:00 Asia/Shanghai，
 * 独立 Bean 保证跨类调用走 @Transactional 代理——与 BackupScheduler 同法）。
 */
@Service
public class TrashService {

    private static final Logger log = LoggerFactory.getLogger(TrashService.class);

    private final PhotoRepository repo;
    private final ExifDataRepository exifRepo;
    private final FilePathResolver filePathResolver;

    public TrashService(PhotoRepository repo, ExifDataRepository exifRepo,
                        FilePathResolver filePathResolver) {
        this.repo = repo;
        this.exifRepo = exifRepo;
        this.filePathResolver = filePathResolver;
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
            filePathResolver.deletePhotoFiles(p);
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
        filePathResolver.deletePhotoFiles(photo);
        repo.delete(photo);
    }
}
