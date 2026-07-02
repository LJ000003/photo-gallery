package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock
    private PhotoRepository photoRepo;
    @Mock
    private TagRepository tagRepo;
    @Mock
    private CategoryRepository catRepo;
    @Mock
    private AlbumRepository albumRepo;
    @Mock
    private ExifService exifService;
    @Mock
    private ExifDataRepository exifRepo;

    private PhotoService service;

    @BeforeEach
    void setUp() {
        service = new PhotoService(photoRepo, tagRepo, catRepo, albumRepo, exifService, exifRepo, "target/test-uploads");
    }

    @Test
    void listAll_shouldCallRepository() {
        Page<Photo> page = new PageImpl<>(List.of());
        when(photoRepo.findAll(any(PageRequest.class))).thenReturn(page);

        Page<Photo> result = service.listAll(null, null, PageRequest.of(0, 20));

        assertThat(result).isSameAs(page);
        verify(photoRepo).findAll(any(PageRequest.class));
    }

    @Test
    void listAll_withTag_shouldCallFindByTagIds() {
        Page<Photo> page = new PageImpl<>(List.of());
        when(photoRepo.findByTagIds(any(), any())).thenReturn(page);

        service.listAll(List.of(1L), null, PageRequest.of(0, 20));

        verify(photoRepo).findByTagIds(List.of(1L), PageRequest.of(0, 20));
        verify(photoRepo, never()).findAll(any(PageRequest.class));
    }

    @Test
    void listAll_withCategory_shouldCallFindByCategoryIds() {
        Page<Photo> page = new PageImpl<>(List.of());
        when(photoRepo.findByCategoryIds(any(), any())).thenReturn(page);

        service.listAll(null, List.of(2L), PageRequest.of(0, 20));

        verify(photoRepo).findByCategoryIds(List.of(2L), PageRequest.of(0, 20));
    }

    @Test
    void getById_found_shouldReturnPhoto() {
        Photo p = new Photo(); p.setId(1L); p.setName("test");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));

        Photo result = service.getById(1L);

        assertThat(result.getName()).isEqualTo("test");
    }

    @Test
    void getById_notFound_shouldThrow() {
        when(photoRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .hasMessageContaining("不存在");
    }

    @Test
    void update_shouldModifyFields() {
        Photo p = new Photo(); p.setId(1L); p.setName("old");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(photoRepo.save(any())).thenReturn(p);

        Photo result = service.update(1L, "newName", "newDesc", null, null, null);

        assertThat(result.getName()).isEqualTo("newName");
        assertThat(result.getDescription()).isEqualTo("newDesc");
    }

    @Test
    void createTag_duplicateName_shouldReturnExisting() {
        Tag existing = new Tag("风景", "#ff0"); existing.setId(1L);
        when(tagRepo.findByName("风景")).thenReturn(Optional.of(existing));

        Tag result = service.createTag("风景", "#f00");

        assertThat(result.getId()).isEqualTo(1L);
        verify(tagRepo, never()).save(any());
    }

    @Test
    void createTag_new_shouldSave() {
        when(tagRepo.findByName("新标签")).thenReturn(Optional.empty());
        when(tagRepo.save(any())).thenAnswer(inv -> { Tag t = inv.getArgument(0); t.setId(10L); return t; });

        Tag result = service.createTag("新标签", "#00f");

        assertThat(result.getId()).isEqualTo(10L);
        verify(tagRepo).save(any());
    }
}
