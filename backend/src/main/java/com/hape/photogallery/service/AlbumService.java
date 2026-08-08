package com.hape.photogallery.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hape.photogallery.dto.AlbumResponse;
import com.hape.photogallery.dto.PhotoResponse;
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
    private final PhotoQueryService photoQueryService;

    public AlbumService(AlbumRepository albumRepo, PhotoRepository photoRepo,
                        PhotoQueryService photoQueryService) {
        this.albumRepo = albumRepo;
        this.photoRepo = photoRepo;
        this.photoQueryService = photoQueryService;
    }

    @Cacheable("albums")
    public List<AlbumResponse> listAll() {
        // photoCount 用一次分组查询填充，不触发 getPhotoCount() 的整集合懒加载
        Map<Long, Integer> counts = photoRepo.countByAlbum().stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
        return albumRepo.findAll().stream()
                .map(a -> AlbumResponse.from(a, counts.getOrDefault(a.getId(), 0)))
                // 缓存值必须收进 ArrayList：stream().toList() 返回 JDK 不可变 ListN（final 类），
                // Redis 的 NON_FINAL typing 不为它写类型 id → 空列表序列化为裸 [] → 读取时
                // SerializationException → GET /api/albums 500（prod Redis 形态曾现故障）
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    @CacheEvict(value = {"albums", "photos"}, allEntries = true)
    @Transactional
    public AlbumResponse create(String name, String description, List<Long> photoIds) {
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
                // 封面用 min id（确定性——HashSet 迭代序随 JVM 漂移）
                a.setCoverPhotoId(minPhotoId(photos));
                albumRepo.save(a);
            }
        }
        // photoCount 用实际加载数（findAllById 过滤不存在/已删 id），与现状 getPhotoCount() 一致
        return AlbumResponse.from(a, a.getPhotos().size());
    }

    /** 确定性封面选择：min id（与 findPhotoIdsByAlbumId 的 ORDER BY id 首元素语义一致） */
    private static Long minPhotoId(Set<Photo> photos) {
        return photos.stream().mapToLong(Photo::getId).min()
                .stream().boxed().findFirst().orElse(null);
    }

    @CacheEvict(value = {"albums", "photos"}, allEntries = true)
    @Transactional
    public AlbumResponse update(Long id, String name, String description, List<Long> photoIds) {
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
            // 仅当原封面不在新集合时才重选（min id）——用户只改名时封面不得无故漂移
            Long current = a.getCoverPhotoId();
            boolean coverKept = current != null
                    && photos.stream().anyMatch(p -> p.getId().equals(current));
            if (!coverKept) {
                a.setCoverPhotoId(minPhotoId(photos));
            }
        }
        albumRepo.save(a);
        return AlbumResponse.from(a, a.getPhotos().size());
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
        // 软删期间照片可能已被删除（reselect 因 Album 的 @SQLRestriction 跳过已删相册，
        // 不会处理它）——恢复后校验封面指向的照片是否仍存在（findById 受照片侧
        // @SQLRestriction 过滤：已软删照片也算不可见），不存在则重选或置 null
        Long coverId = a.getCoverPhotoId();
        if (coverId != null && photoRepo.findById(coverId).isEmpty()) {
            reselectCoversAfterPhotoDeletion(coverId);
        }
    }

    /** 回收站相册（已 DTO 化；分组计数不含已删相册，photoCount 一律 0——回收站 UI 不显示计数） */
    public List<AlbumResponse> listDeleted() {
        return albumRepo.findDeleted().stream()
                .map(a -> AlbumResponse.from(a, 0))
                .toList();
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

    /** 相册详情（photoCount 用轻量投影，不触发整集合懒加载） */
    public AlbumResponse getAlbum(Long id) {
        Album a = albumRepo.findById(id).orElseThrow(() -> new BusinessException(404, "相册不存在"));
        return AlbumResponse.from(a, photoRepo.findPhotoIdsByAlbumId(id).size());
    }

    public Page<Photo> listPhotos(Long albumId, Pageable pageable) {
        // 不存在/已删除的相册返回 404，而非静默空列表（与 /photos/{id} 的资源缺失语义一致）
        albumRepo.findById(albumId).orElseThrow(() -> new BusinessException(404, "相册不存在"));
        return photoRepo.findByAlbumId(albumId, pageable);
    }

    /** 相册照片列表（事务内 map toResponse——懒加载代理序列化防护，同 PhotoQueryService.searchResponses） */
    @Transactional(readOnly = true)
    public Page<PhotoResponse> listPhotosResponses(Long albumId, Pageable pageable) {
        return listPhotos(albumId, pageable).map(photoQueryService::toResponse);
    }

    /** 未分配相册照片列表（事务内 map，同上） */
    @Transactional(readOnly = true)
    public Page<PhotoResponse> listUnassignedResponses(Pageable pageable) {
        return photoRepo.findUnassigned(pageable).map(photoQueryService::toResponse);
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
        // 封面取实际加载成功的照片中的 min id（与 create/update/removePhotos 的 minPhotoId
        // 约定一致——此前取请求列表首个 id，同一批照片因传参顺序不同封面不同）。
        // 不取 photoIds.get(0)：可能不存在（软删/拼错 id），直接设置会产生幽灵封面
        // （曾见：封面空白且永不自愈）
        List<Long> loadedIds = new ArrayList<>();
        for (Long pid : photoIds) {
            Photo p = photoRepo.findById(pid).orElse(null);
            if (p != null) {
                a.getPhotos().add(p);
                p.getAlbums().add(a);
                photoRepo.save(p);
                loadedIds.add(pid);
            }
        }
        if (a.getCoverPhotoId() == null && !loadedIds.isEmpty()) {
            a.setCoverPhotoId(loadedIds.stream().min(Long::compare).orElse(null));
        }
        albumRepo.save(a);
    }

    /**
     * 照片被删除（软删/彻底删除）后重选以其为封面的相册：
     * 剩余未删照片第一张（findPhotoIdsByAlbumId 受 @SQLRestriction 过滤 + ORDER BY id
     * 确定性行序，软删照片立即不参与重选）或置 null。调用方（PhotoService.delete /
     * TrashService）在删除事务内调用，REQUIRED 并入外层事务，失败整体回滚。
     * 已知局限（TOCTOU）：并发删除同相册多张照片时，READ_COMMITTED 下读不到对方
     * 未提交的 deleted_at，可能选中已被并发删除的照片——管理员低频操作，可接受。
     */
    @Transactional
    @CacheEvict(value = {"albums", "photos"}, allEntries = true)
    public void reselectCoversAfterPhotoDeletion(Long photoId) {
        for (Album a : albumRepo.findByCoverPhotoId(photoId)) {
            List<Long> remaining = photoRepo.findPhotoIdsByAlbumId(a.getId());
            a.setCoverPhotoId(remaining.isEmpty() ? null : remaining.get(0));
            albumRepo.save(a);
        }
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
            // 封面被移除时重选：min id（确定性——HashSet 迭代序随 JVM 漂移）
            a.setCoverPhotoId(minPhotoId(a.getPhotos()));
        }
        albumRepo.save(a);
    }

    @Transactional
    public void syncPhotoAlbums(Photo photo, List<Long> albumIds) {
        for (Album a : new HashSet<>(photo.getAlbums())) {
            if (!albumIds.contains(a.getId())) {
                a.getPhotos().remove(photo);
                photo.getAlbums().remove(a);
                // 照片被移出后封面不得悬空（指向不在相册中的照片）——重选或置 null
                if (a.getCoverPhotoId() != null && a.getCoverPhotoId().equals(photo.getId())) {
                    a.setCoverPhotoId(minPhotoId(a.getPhotos()));
                }
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
