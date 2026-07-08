package com.hape.photogallery.repository;

import com.hape.photogallery.entity.Category;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PhotoRepositoryTest {

    @Autowired
    private PhotoRepository photoRepo;

    @Autowired
    private TagRepository tagRepo;

    @Autowired
    private CategoryRepository catRepo;

    private Category cat1;
    private Tag tag1, tag2;

    @BeforeEach
    void setUp() {
        cat1 = catRepo.save(new Category("风景"));
        Category cat2 = catRepo.save(new Category("人像"));
        tag1 = tagRepo.save(new Tag("日出", "#ff8800"));
        tag2 = tagRepo.save(new Tag("海边", "#0088ff"));

        Photo p1 = new Photo(); p1.setName("a"); p1.setCategory(cat1); p1.setFileName("a.jpg");
        p1.getTags().add(tag1);
        Photo p2 = new Photo(); p2.setName("b"); p2.setCategory(cat1); p2.setFileName("b.jpg");
        p2.getTags().add(tag1); p2.getTags().add(tag2);
        Photo p3 = new Photo(); p3.setName("c"); p3.setCategory(cat2); p3.setFileName("c.jpg");
        p3.getTags().add(tag2);
        photoRepo.saveAll(List.of(p1, p2, p3));
    }

    @Test
    void findByCategoryIds_shouldReturnMatchingPhotos() {
        Page<Photo> page = photoRepo.findByCategoryIds(List.of(cat1.getId()), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByTagIds_shouldReturnMatchingPhotos() {
        Page<Photo> page = photoRepo.findByTagIds(List.of(tag1.getId()), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByTagIds_noMatch_shouldReturnEmpty() {
        Page<Photo> page = photoRepo.findByTagIds(List.of(9999L), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(0);
    }

    @Test
    void findByCategoryIdsAndTagIds_shouldIntersect() {
        Page<Photo> page = photoRepo.findByCategoryIdsAndTagIds(
                List.of(cat1.getId()), List.of(tag2.getId()), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}
