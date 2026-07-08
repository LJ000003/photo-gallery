package com.hape.photogallery.service;

import com.hape.photogallery.entity.Tag;
import com.hape.photogallery.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepo;

    private TagService service;

    @BeforeEach
    void setUp() {
        service = new TagService(tagRepo);
    }

    @Test
    void createTag_duplicateName_shouldReturnExisting() {
        Tag existing = new Tag("风景", "#ff0"); existing.setId(1L);
        when(tagRepo.findByName("风景")).thenReturn(Optional.of(existing));

        Tag result = service.create("风景", "#f00");

        assertThat(result.getId()).isEqualTo(1L);
        verify(tagRepo, never()).save(any());
    }

    @Test
    void createTag_new_shouldSave() {
        when(tagRepo.findByName("新标签")).thenReturn(Optional.empty());
        when(tagRepo.save(any())).thenAnswer(inv -> { Tag t = inv.getArgument(0); t.setId(10L); return t; });

        Tag result = service.create("新标签", "#00f");

        assertThat(result.getId()).isEqualTo(10L);
        verify(tagRepo).save(any());
    }
}
