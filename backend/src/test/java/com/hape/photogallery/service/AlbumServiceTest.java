package com.hape.photogallery.service;

import com.hape.photogallery.entity.Album;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.AlbumRepository;
import com.hape.photogallery.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlbumServiceTest {

    @Mock private AlbumRepository albumRepo;
    @Mock private PhotoRepository photoRepo;
    @Mock private PhotoQueryService photoQueryService;

    private AlbumService service;

    @BeforeEach
    void setUp() {
        service = new AlbumService(albumRepo, photoRepo, photoQueryService);
    }

    // ==================== listAll ====================

    @Test
    void listAll_shouldReturnAllAlbumsWithCounts() {
        Album a1 = new Album("a1");
        a1.setId(1L);
        when(albumRepo.findAll()).thenReturn(List.of(a1));
        when(photoRepo.countByAlbum()).thenReturn(List.<Object[]>of(new Object[]{1L, 3L}));
        var result = service.listAll();
        assertThat(result).hasSize(1);
        // photoCount 来自分组计数查询（一次查询填全量），不再逐相册懒加载
        assertThat(result.get(0).getPhotoCount()).isEqualTo(3);
    }

    @Test
    void listAll_countQueryMiss_shouldDefaultZero() {
        when(albumRepo.findAll()).thenReturn(List.of(new Album("a1")));
        when(photoRepo.countByAlbum()).thenReturn(List.of());
        assertThat(service.listAll().get(0).getPhotoCount()).isZero();
    }

    // ==================== create ====================

    @Test
    void create_shouldSaveAlbum() {
        Album a = new Album("new"); a.setId(1L);
        when(albumRepo.save(any(Album.class))).thenReturn(a);

        var result = service.create("new", "desc", null);
        assertThat(result.getName()).isEqualTo("new");
        assertThat(result.getPhotoCount()).isZero();
    }

    @Test
    void create_withPhotos_shouldAssociatePhotos() {
        Album a = new Album("new"); a.setId(1L);
        when(albumRepo.save(any(Album.class))).thenReturn(a);

        Photo p1 = new Photo(); p1.setId(5L); p1.setName("p5");
        Photo p2 = new Photo(); p2.setId(2L); p2.setName("p2");
        when(photoRepo.findAllById(List.of(5L, 2L))).thenReturn(List.of(p1, p2));

        var result = service.create("new", null, List.of(5L, 2L));

        verify(photoRepo).save(p1);
        verify(photoRepo).save(p2);
        // 封面确定性：min id（HashSet 迭代序随 JVM 漂移，不得依赖迭代首元素）
        assertThat(result.getCoverPhotoId()).isEqualTo(2L);
    }

    // ==================== update ====================

    @Test
    void update_shouldModifyAlbum() {
        Album a = new Album("old"); a.setId(1L);
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a));
        when(albumRepo.save(any())).thenReturn(a);

        var result = service.update(1L, "newName", "newDesc", null);
        assertThat(result.getName()).isEqualTo("newName");
        assertThat(result.getDescription()).isEqualTo("newDesc");
    }

    @Test
    void update_notFound_shouldThrow() {
        when(albumRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(99L, "x", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("相册不存在");
    }

    @Test
    void update_withPhotoIds_coverStillInSet_shouldKeepCover() {
        Album a = new Album("old"); a.setId(1L);
        a.setCoverPhotoId(3L);
        Photo p3 = new Photo(); p3.setId(3L); p3.setName("p3");
        Photo p7 = new Photo(); p7.setId(7L); p7.setName("p7");
        a.getPhotos().add(p7);
        p7.getAlbums().add(a);
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a));
        when(albumRepo.save(any())).thenReturn(a);
        when(photoRepo.findAllById(List.of(3L, 7L))).thenReturn(List.of(p3, p7));

        service.update(1L, "newName", null, List.of(3L, 7L));

        // 用户只改照片集合（封面仍在其中）时封面不得无故漂移
        assertThat(a.getCoverPhotoId()).isEqualTo(3L);
    }

    @Test
    void update_withPhotoIds_coverRemoved_shouldReselectMin() {
        Album a = new Album("old"); a.setId(1L);
        a.setCoverPhotoId(3L);
        Photo p3 = new Photo(); p3.setId(3L); p3.setName("p3");
        Photo p7 = new Photo(); p7.setId(7L); p7.setName("p7");
        a.getPhotos().add(p3);
        a.getPhotos().add(p7);
        p3.getAlbums().add(a);
        p7.getAlbums().add(a);
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a));
        when(albumRepo.save(any())).thenReturn(a);
        when(photoRepo.findAllById(List.of(7L))).thenReturn(List.of(p7));

        service.update(1L, "newName", null, List.of(7L));

        // 原封面被移出新集合 → 重选为剩余 min id（确定性）
        assertThat(a.getCoverPhotoId()).isEqualTo(7L);
    }

    // ==================== delete ====================

    @Test
    void delete_shouldSoftDelete() {
        Album a = new Album("a"); a.setId(1L);
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a));

        service.delete(1L);
        verify(albumRepo).save(a);
        assertThat(a.getDeletedAt()).isNotNull();
    }

    @Test
    void delete_withPhotos_shouldKeepAssociations() {
        Album a = new Album("a"); a.setId(1L);
        Photo p = new Photo(); p.setId(1L); p.setName("p1");
        a.getPhotos().add(p);
        p.getAlbums().add(a);
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a));

        service.delete(1L);

        verify(photoRepo, never()).save(any(Photo.class));
        assertThat(p.getAlbums()).hasSize(1); // 软删除保留关联
    }

    // ==================== listPhotos ====================

    @Test
    void listPhotos_shouldCallRepository() {
        Album a = new Album("a"); a.setId(1L);
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a));
        when(photoRepo.findByAlbumId(eq(1L), any())).thenReturn(new PageImpl<>(List.of()));
        Page<Photo> result = service.listPhotos(1L, PageRequest.of(0, 20));
        assertThat(result).isEmpty();
    }

    @Test
    void listPhotos_albumNotFound_shouldThrow404() {
        when(albumRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listPhotos(99L, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("相册不存在");
        verify(photoRepo, never()).findByAlbumId(any(), any());
    }

    // ==================== listUnassigned ====================

    @Test
    void listUnassigned_shouldCallRepository() {
        when(photoRepo.findUnassigned(any())).thenReturn(new PageImpl<>(List.of()));
        Page<Photo> result = service.listUnassigned(PageRequest.of(0, 20));
        assertThat(result).isEmpty();
    }

    // ==================== addPhotos / removePhotos ====================

    @Test
    void addPhotos_shouldAssociatePhotos() {
        Album a = new Album("a"); a.setId(1L);
        Photo p = new Photo(); p.setId(10L); p.setName("p10");
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a));
        when(photoRepo.findById(10L)).thenReturn(Optional.of(p));

        service.addPhotos(1L, List.of(10L));

        assertThat(a.getPhotos()).contains(p);
        assertThat(a.getCoverPhotoId()).isEqualTo(10L);
        verify(photoRepo).save(p);
    }

    @Test
    void addPhotos_firstIdMissing_shouldUseFirstLoadedPhotoAsCover() {
        Album a = new Album("a"); a.setId(1L); // 无封面
        Photo p = new Photo(); p.setId(10L); p.setName("p10");
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a));
        // photoIds 首元素不存在（软删/拼错 id）——不得产生幽灵封面
        when(photoRepo.findById(999L)).thenReturn(Optional.empty());
        when(photoRepo.findById(10L)).thenReturn(Optional.of(p));

        service.addPhotos(1L, List.of(999L, 10L));

        assertThat(a.getPhotos()).contains(p);
        assertThat(a.getCoverPhotoId()).isEqualTo(10L);
    }

    @Test
    void addPhotos_allIdsMissing_shouldKeepCoverNull() {
        Album a = new Album("a"); a.setId(1L);
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a));
        when(photoRepo.findById(999L)).thenReturn(Optional.empty());

        service.addPhotos(1L, List.of(999L));

        assertThat(a.getPhotos()).isEmpty();
        assertThat(a.getCoverPhotoId()).isNull();
    }

    // ==================== getAlbum（相册详情端点补缺） ====================

    @Test
    void getAlbum_shouldReturnAlbumWithCount() {
        Album a = new Album("相册");
        a.setId(1L);
        a.setCoverPhotoId(3L);
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a));
        when(photoRepo.findPhotoIdsByAlbumId(1L)).thenReturn(List.of(3L, 7L));

        var result = service.getAlbum(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCoverPhotoId()).isEqualTo(3L);
        assertThat(result.getPhotoCount()).isEqualTo(2);
    }

    @Test
    void getAlbum_notFound_shouldThrow404() {
        when(albumRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAlbum(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("相册不存在");
    }

    @Test
    void removePhotos_shouldDisassociatePhotos() {
        Album a = new Album("a"); a.setId(1L); a.setCoverPhotoId(99L);
        Photo p = new Photo(); p.setId(10L); p.setName("p10");
        a.getPhotos().add(p);
        p.getAlbums().add(a);
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a));
        when(photoRepo.findById(10L)).thenReturn(Optional.of(p));

        service.removePhotos(1L, List.of(10L));

        assertThat(a.getPhotos()).doesNotContain(p);
        verify(photoRepo).save(p);
    }

    @Test
    void removePhotos_coverRemoved_shouldReselectRemaining() {
        Album a = new Album("a"); a.setId(1L);
        a.setCoverPhotoId(10L); // 被移除的照片是封面
        Photo p10 = new Photo(); p10.setId(10L); p10.setName("p10");
        Photo p11 = new Photo(); p11.setId(11L); p11.setName("p11");
        a.getPhotos().add(p10);
        a.getPhotos().add(p11);
        p10.getAlbums().add(a);
        p11.getAlbums().add(a);
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a));
        when(photoRepo.findById(10L)).thenReturn(Optional.of(p10));

        service.removePhotos(1L, List.of(10L));

        // 封面重选为剩余 min id（确定性——HashSet 迭代序不可依赖）
        assertThat(a.getCoverPhotoId()).isEqualTo(11L);
    }

    @Test
    void removePhotos_coverRemoved_noRemaining_shouldSetNull() {
        Album a = new Album("a"); a.setId(1L);
        a.setCoverPhotoId(10L);
        Photo p10 = new Photo(); p10.setId(10L); p10.setName("p10");
        a.getPhotos().add(p10);
        p10.getAlbums().add(a);
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a));
        when(photoRepo.findById(10L)).thenReturn(Optional.of(p10));

        service.removePhotos(1L, List.of(10L));

        assertThat(a.getCoverPhotoId()).isNull();
    }

    // ==================== syncPhotoAlbums ====================

    @Test
    void syncPhotoAlbums_shouldAddAndRemove() {
        Album a1 = new Album("a1"); a1.setId(1L);
        Album a2 = new Album("a2"); a2.setId(2L);
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a1));

        Photo p = new Photo(); p.setId(1L); p.setName("p1");
        p.getAlbums().add(a2);
        a2.getPhotos().add(p);

        service.syncPhotoAlbums(p, List.of(1L));

        // a1 should gain the photo
        assertThat(a1.getPhotos()).contains(p);
        // a2 should lose it
        assertThat(a2.getPhotos()).doesNotContain(p);
    }

    @Test
    void syncPhotoAlbums_coverPhotoRemoved_shouldReselectOrNull() {
        Album a1 = new Album("a1"); a1.setId(1L);
        Album a2 = new Album("a2"); a2.setId(2L);
        a2.setCoverPhotoId(1L); // 封面就是被移出的照片（悬空封面，P0 修复）
        when(albumRepo.findById(1L)).thenReturn(Optional.of(a1));

        Photo p = new Photo(); p.setId(1L); p.setName("p1");
        p.getAlbums().add(a2);
        a2.getPhotos().add(p);

        service.syncPhotoAlbums(p, List.of(1L));

        // 被移除后 a2 无剩余照片 → 封面置 null，不悬空
        assertThat(a2.getPhotos()).doesNotContain(p);
        assertThat(a2.getCoverPhotoId()).isNull();
    }

    // ==================== 回收站：恢复 / 永久删除 / 列表 ====================

    @Test
    void restore_success_shouldClearDeletedAt() {
        Album a = new Album("已删"); a.setId(1L);
        a.setDeletedAt(java.time.LocalDateTime.now());
        when(albumRepo.findDeletedById(1L)).thenReturn(Optional.of(a));

        service.restore(1L);

        assertThat(a.getDeletedAt()).isNull();
        verify(albumRepo).save(a);
    }

    @Test
    void restore_notFound_should404() {
        when(albumRepo.findDeletedById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.restore(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到可恢复的相册");
        verify(albumRepo, never()).save(any());
    }

    @Test
    void permanentlyDelete_shouldDisassociatePhotos() {
        Album a = new Album("已删"); a.setId(1L);
        Photo p = new Photo(); p.setId(10L); p.setName("p10");
        a.getPhotos().add(p);
        p.getAlbums().add(a);
        when(albumRepo.findDeletedById(1L)).thenReturn(Optional.of(a));

        service.permanentlyDelete(1L);

        verify(albumRepo).delete(a);
        assertThat(p.getAlbums()).doesNotContain(a);
        assertThat(a.getPhotos()).isEmpty();
    }

    @Test
    void permanentlyDelete_notFound_should404() {
        when(albumRepo.findDeletedById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.permanentlyDelete(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到该相册");
        verify(albumRepo, never()).delete(any());
    }

    @Test
    void listDeleted_photoCountShouldAlwaysBeZero() {
        Album a = new Album("已删"); a.setId(1L);
        Photo p = new Photo(); p.setId(10L); p.setName("p10");
        a.getPhotos().add(p);
        when(albumRepo.findDeleted()).thenReturn(List.of(a));

        var result = service.listDeleted();

        // 回收站 UI 不显示计数，photoCount 一律 0（即使实体仍有关联照片）
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPhotoCount()).isZero();
    }

    // ==================== 封面重选（P1 修复：照片删除后封面悬空） ====================

    @Test
    void reselectCoversAfterPhotoDeletion_withRemainingPhotos_shouldPickFirst() {
        Album a = new Album("相册");
        a.setId(1L);
        a.setCoverPhotoId(9L); // 被删照片
        when(albumRepo.findByCoverPhotoId(9L)).thenReturn(List.of(a));
        // 剩余未删照片（findPhotoIdsByAlbumId 受 @SQLRestriction 过滤，软删照片不出现）
        when(photoRepo.findPhotoIdsByAlbumId(1L)).thenReturn(List.of(3L, 7L));

        service.reselectCoversAfterPhotoDeletion(9L);

        assertThat(a.getCoverPhotoId()).isEqualTo(3L);
        verify(albumRepo).save(a);
    }

    @Test
    void reselectCoversAfterPhotoDeletion_noRemaining_shouldSetNull() {
        Album a = new Album("空相册");
        a.setId(1L);
        a.setCoverPhotoId(9L);
        when(albumRepo.findByCoverPhotoId(9L)).thenReturn(List.of(a));
        when(photoRepo.findPhotoIdsByAlbumId(1L)).thenReturn(List.of());

        service.reselectCoversAfterPhotoDeletion(9L);

        assertThat(a.getCoverPhotoId()).isNull();
        verify(albumRepo).save(a);
    }

    @Test
    void reselectCoversAfterPhotoDeletion_noAlbumTouching_shouldNoOp() {
        when(albumRepo.findByCoverPhotoId(9L)).thenReturn(List.of());

        service.reselectCoversAfterPhotoDeletion(9L);

        verify(albumRepo, never()).save(any());
    }
}
