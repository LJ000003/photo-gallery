package com.hape.photogallery.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hape.photogallery.entity.Album;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.AlbumRepository;
import com.hape.photogallery.repository.PhotoRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlbumService {

    // evict 对照表（聚合根 → 依赖缓存）：
    //   addPhotos/removePhotos 会改 coverPhotoId（首次加入设封面 / 移除封面照片）→ albums 列表
    //   展示的封面会变，必须连带失效 albums（曾只 evict photos，封面最长 30s 显示旧图）
    //   syncPhotoAlbums 无自 evict，依赖 PhotoService.update 的 4-5 缓存 evict 兜底——收紧 update 清单时勿忘
    private final AlbumRepository albumRepo;
    private final PhotoRepository photoRepo;

    public AlbumService(AlbumRepository albumRepo, PhotoRepository photoRepo) {
        this.albumRepo = albumRepo;
        this.photoRepo = photoRepo;
    }

    @Cacheable("albums")
    public List<Album> listAll() {
        return albumRepo.findAll();
    }

    @CacheEvict(value = {"albums", "photos"}, allEntries = true)
    @Transactional
    public Album create(String name, String description, List<Long> photoIds) {
        Album a = new Album(name);
        a.setDescription(description);
        a = albumRepo.save(a);
        if (photoIds != null && !photoIds.isEmpty()) {
            Set<Photo> photos = new HashSet<>(photoRepo.findAllById(photoIds));
            if (!photos.isEmpty()) {
                a.setPhotos(photos);
                for (Photo p : photos) {
                    p.getAlbums().add(a);
                    photoRepo.save(p);
                }
                // 使用实际加载到的第一张照片作为封面
                a.setCoverPhotoId(photos.iterator().next().getId());
                albumRepo.save(a);
            }
        }
        return a;
    }

    @CacheEvict(value = {"albums", "photos"}, allEntries = true)
    @Transactional
    public Album update(Long id, String name, String description, List<Long> photoIds) {
        Album a = albumRepo.findById(id).orElseThrow(() -> new BusinessException(404, "相册不存在"));
        if (name != null) a.setName(name);
        if (description != null) a.setDescription(description);
        if (photoIds != null) {
            // 先加载新关联的照片，校验有效性后再清理旧关联（保证原子性）
            Set<Photo> photos = photoIds.isEmpty()
                    ? new HashSet<>()
                    : new HashSet<>(photoRepo.findAllById(photoIds));

            for (Photo p : new HashSet<>(a.getPhotos())) {
                p.getAlbums().remove(a);
                photoRepo.save(p);
            }
            a.getPhotos().clear();

            for (Photo p : photos) {
                p.getAlbums().add(a);
                photoRepo.save(p);
            }
            a.setPhotos(photos);
            if (!photos.isEmpty()) {
                a.setCoverPhotoId(photos.iterator().next().getId());
            } else {
                a.setCoverPhotoId(null);
            }
        }
        return albumRepo.save(a);
    }

    @Transactional
    @CacheEvict(value = {"albums", "photos"}, allEntries = true)
    public void delete(Long id) {
        Album a = albumRepo.findById(id).orElseThrow(() -> new BusinessException(404, "相册不存在"));
        a.setDeletedAt(LocalDateTime.now());
        albumRepo.save(a);
    }

    @Transactional
    @CacheEvict(value = {"albums", "photos"}, allEntries = true)
    public void restore(Long id) {
        Album a = albumRepo.findDeletedById(id)
                .orElseThrow(() -> new BusinessException(404, "未找到可恢复的相册"));
        a.setDeletedAt(null);
        albumRepo.save(a);
    }

    public List<Album> listDeleted() {
        return albumRepo.findDeleted();
    }

    @Transactional
    @CacheEvict(value = {"albums", "photos"}, allEntries = true)
    public void permanentlyDelete(Long id) {
        Album a = albumRepo.findDeletedById(id)
                .orElseThrow(() -> new BusinessException(404, "未找到该相册"));
        for (Photo p : new HashSet<>(a.getPhotos())) {
            p.getAlbums().remove(a);
        }
        a.getPhotos().clear();
        albumRepo.delete(a);
    }

    public Page<Photo> listPhotos(Long albumId, Pageable pageable) {
        // 不存在/已删除的相册返回 404，而非静默空列表（与 /photos/{id} 的资源缺失语义一致）
        albumRepo.findById(albumId).orElseThrow(() -> new BusinessException(404, "相册不存在"));
        return photoRepo.findByAlbumId(albumId, pageable);
    }

    /** 相册内照片 id 列表（轻量投影，编辑抽屉预选初始化用） */
    public List<Long> listPhotoIds(Long albumId) {
        albumRepo.findById(albumId).orElseThrow(() -> new BusinessException(404, "相册不存在"));
        return photoRepo.findPhotoIdsByAlbumId(albumId);
    }

    public Page<Photo> listUnassigned(Pageable pageable) {
        return photoRepo.findUnassigned(pageable);
    }

    @Transactional
    @CacheEvict(value = {"photos", "albums"}, allEntries = true)
    public void addPhotos(Long albumId, List<Long> photoIds) {
        Album a = albumRepo.findById(albumId).orElseThrow(() -> new BusinessException(404, "相册不存在"));
        for (Long pid : photoIds) {
            Photo p = photoRepo.findById(pid).orElse(null);
            if (p != null) {
                a.getPhotos().add(p);
                p.getAlbums().add(a);
                photoRepo.save(p);
            }
        }
        if (a.getCoverPhotoId() == null && !photoIds.isEmpty()) {
            a.setCoverPhotoId(photoIds.get(0));
        }
        albumRepo.save(a);
    }

    @Transactional
    @CacheEvict(value = {"photos", "albums"}, allEntries = true)
    public void removePhotos(Long albumId, List<Long> photoIds) {
        Album a = albumRepo.findById(albumId).orElseThrow(() -> new BusinessException(404, "相册不存在"));
        for (Long pid : photoIds) {
            Photo p = photoRepo.findById(pid).orElse(null);
            if (p != null) {
                a.getPhotos().remove(p);
                p.getAlbums().remove(a);
                photoRepo.save(p);
            }
        }
        if (a.getCoverPhotoId() != null && photoIds.contains(a.getCoverPhotoId())) {
            // 封面被移除时，选择剩余第一张作为新封面（使用有序集合保证确定性）
            a.setCoverPhotoId(a.getPhotos().isEmpty() ? null
                    : new java.util.ArrayList<>(a.getPhotos()).get(0).getId());
        }
        albumRepo.save(a);
    }

    @Transactional
    public void syncPhotoAlbums(Photo photo, List<Long> albumIds) {
        for (Album a : new HashSet<>(photo.getAlbums())) {
            if (!albumIds.contains(a.getId())) {
                a.getPhotos().remove(photo);
                photo.getAlbums().remove(a);
                albumRepo.save(a);
            }
        }
        for (Long aid : albumIds) {
            if (photo.getAlbums().stream().noneMatch(a -> a.getId().equals(aid))) {
                Album a = albumRepo.findById(aid).orElse(null);
                if (a != null) {
                    a.getPhotos().add(photo);
                    photo.getAlbums().add(a);
                    albumRepo.save(a);
                }
            }
        }
    }
}
