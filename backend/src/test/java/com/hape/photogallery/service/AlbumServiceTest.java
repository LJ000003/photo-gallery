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

    private AlbumService service;

    @BeforeEach
    void setUp() {
        service = new AlbumService(albumRepo, photoRepo);
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
        // P4-#38：photoCount 来自分组计数查询（一次查询填全量），不再逐相册懒加载
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

        Photo p = new Photo(); p.setId(1L); p.setName("p1");
        when(photoRepo.findAllById(List.of(1L))).thenReturn(List.of(p));

        service.create("new", null, List.of(1L));

        verify(photoRepo).save(p);
        verify(albumRepo, atLeastOnce()).save(any());
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
        verify(photoRepo).save(p);
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

    // ==================== 回收站：恢复 / 永久删除 / 列表（P1-#14） ====================

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

        // P4-#38：回收站 UI 不显示计数，photoCount 一律 0（即使实体仍有关联照片）
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPhotoCount()).isZero();
    }
}
