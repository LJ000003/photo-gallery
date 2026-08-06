package com.hape.photogallery.repository;

import com.hape.photogallery.entity.Album;
import com.hape.photogallery.entity.Category;
import com.hape.photogallery.entity.ExifData;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.Tag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PhotoRepositoryTest {

    @Autowired
    private PhotoRepository photoRepo;

    @Autowired
    private TagRepository tagRepo;

    @Autowired
    private CategoryRepository catRepo;

    @Autowired
    private AlbumRepository albumRepo;

    @Autowired
    private ExifDataRepository exifRepo;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private EntityManagerFactory emf;

    private Category cat1, cat2;
    private Tag tag1, tag2;

    @BeforeEach
    void setUp() {
        cat1 = catRepo.save(new Category("风景"));
        cat2 = catRepo.save(new Category("人像"));
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

    // ==================== search ====================
    // H2 不支持 MySQL FULLTEXT，需 MySQL 环境手动验证

    @Disabled("H2 不支持 MySQL FULLTEXT，需 MySQL 环境验证")
    @Test
    void search_shouldFindByName() {
        Page<Photo> page = photoRepo.search("a", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Disabled("H2 不支持 MySQL FULLTEXT，需 MySQL 环境验证")
    @Test
    void search_shouldBeCaseInsensitive() {
        Page<Photo> page = photoRepo.search("A", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Disabled("H2 不支持 MySQL FULLTEXT，需 MySQL 环境验证")
    @Test
    void search_noMatch_shouldReturnEmpty() {
        Page<Photo> page = photoRepo.search("nonexistent", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(0);
    }

    // —— LIKE fallback（单字搜索 <2 字符时 PhotoService 走此分支；H2 可真实执行）——
    // 注意 H2 的 LIKE 默认大小写敏感（MySQL collation 不敏感），pattern 一律小写
    @Test
    void searchByLike_shouldFindByName() {
        Page<Photo> page = photoRepo.searchByLike("%a%", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getName()).isEqualTo("a");
    }

    @Test
    void searchByLikeWithTagIds_shouldFilterByTag() {
        Page<Photo> page = photoRepo.searchByLikeWithTagIds(
                "%a%", List.of(tag1.getId()), PageRequest.of(0, 10));
        // p1(name=a) 含 tag1；p2(name=b) 也含 tag1 但不匹配 pattern
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchByLikeWithCategoryIds_shouldFilterByCategory() {
        Page<Photo> page = photoRepo.searchByLikeWithCategoryIds(
                "%a%", List.of(cat1.getId()), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchByLikeWithTagAndCategoryIds_shouldIntersect() {
        Page<Photo> page = photoRepo.searchByLikeWithTagAndCategoryIds(
                "%a%", List.of(tag2.getId()), List.of(cat1.getId()), PageRequest.of(0, 10));
        // cat1 内含 tag2 的只有 p2(name=b)，不匹配 "%a%"
        assertThat(page.getTotalElements()).isEqualTo(0);
    }

    // ==================== soft delete ====================

    @Test
    void softDelete_shouldExcludeFromFindAll() {
        Photo toDelete = photoRepo.findAll(PageRequest.of(0, 10))
                .getContent().get(0);
        toDelete.setDeletedAt(java.time.LocalDateTime.now());
        photoRepo.save(toDelete);

        Page<Photo> page = photoRepo.findAll(PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findDeletedById_shouldFindSoftDeleted() {
        Photo toDelete = photoRepo.findAll(PageRequest.of(0, 10))
                .getContent().get(0);
        toDelete.setDeletedAt(java.time.LocalDateTime.now());
        photoRepo.save(toDelete);

        assertThat(photoRepo.findDeletedById(toDelete.getId())).isPresent();
    }

    @Test
    void findDeleted_shouldListDeleted() {
        Photo toDelete = photoRepo.findAll(PageRequest.of(0, 10))
                .getContent().get(0);
        toDelete.setDeletedAt(java.time.LocalDateTime.now());
        photoRepo.save(toDelete);

        Page<Photo> page = photoRepo.findDeleted(PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findDeletedBefore_shouldFilterByDate() {
        Photo toDelete = photoRepo.findAll(PageRequest.of(0, 10))
                .getContent().get(0);
        toDelete.setDeletedAt(java.time.LocalDateTime.now().minusDays(31));
        photoRepo.save(toDelete);

        List<Photo> expired = photoRepo.findDeletedBefore(
                java.time.LocalDateTime.now().minusDays(30));
        assertThat(expired).hasSize(1);
    }

    // ==================== findForBackup（备份导出筛选） ====================

    @Test
    void findForBackup_noFilters_shouldReturnAll() {
        List<Photo> result = photoRepo.findForBackup(null, null, null, null);
        assertThat(result).hasSize(3);
    }

    @Test
    void findForBackup_byAlbum_shouldReturnAlbumPhotosOnly() {
        Album album = albumRepo.save(new Album("旅行"));
        Photo p1 = photoRepo.findAll(PageRequest.of(0, 10)).getContent().get(0);
        album.getPhotos().add(p1);
        p1.getAlbums().add(album);
        albumRepo.save(album);
        photoRepo.save(p1);

        List<Photo> result = photoRepo.findForBackup(album.getId(), null, null, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(p1.getId());
    }

    @Test
    void findForBackup_albumZero_shouldReturnUnassignedOnly() {
        Album album = albumRepo.save(new Album("旅行"));
        Photo p1 = photoRepo.findAll(PageRequest.of(0, 10)).getContent().get(0);
        album.getPhotos().add(p1);
        p1.getAlbums().add(album);
        albumRepo.save(album);
        photoRepo.save(p1);

        List<Photo> result = photoRepo.findForBackup(0L, null, null, null);
        assertThat(result).hasSize(2);
    }

    @Test
    void findForBackup_byCategory_shouldReturnCategoryPhotos() {
        List<Photo> result = photoRepo.findForBackup(null, cat1.getId(), null, null);
        assertThat(result).hasSize(2);
    }

    @Test
    void findForBackup_byDateRange_shouldReturnPhotosInRange() {
        List<Photo> all = photoRepo.findAll(PageRequest.of(0, 10)).getContent();
        LocalDateTime now = LocalDateTime.now();
        all.get(0).setCreatedAt(now.minusDays(10));
        all.get(1).setCreatedAt(now.minusDays(5));
        all.get(2).setCreatedAt(now.minusDays(1));
        photoRepo.saveAll(all);

        List<Photo> result = photoRepo.findForBackup(null, null,
                now.minusDays(6), now.minusDays(2));
        assertThat(result).hasSize(1);
    }

    @Test
    void findForBackup_combinedFilters_shouldIntersect() {
        LocalDateTime now = LocalDateTime.now();
        Photo p1 = photoRepo.findAll(PageRequest.of(0, 10)).getContent().get(0);
        p1.setCreatedAt(now.minusDays(3));
        photoRepo.save(p1);

        // cat1 且 3 天内的照片只有 p1
        List<Photo> result = photoRepo.findForBackup(null, cat1.getId(),
                now.minusDays(7), now);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(p1.getId());
    }

    @Test
    void findForBackup_noMatch_shouldReturnEmpty() {
        List<Photo> result = photoRepo.findForBackup(null, 9999L, null, null);
        assertThat(result).isEmpty();
    }

    // ==================== 列表页 exifData 懒加载批量（类级 @BatchSize 回归保护） ====================
    // 先测后改：Photo.exifData 无字段级 @BatchSize，但 ExifData 类级 @BatchSize(20) 已生效——
    // 期望不加任何代码此用例即通过（查询数 ≈ 3 而非 N+1 的 27），作为回归防护留存。

    @Test
    void exifData_shouldLoadInBatches_notOnePerRow() {
        List<Photo> photos = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            Photo p = new Photo();
            p.setName("batch-" + i);
            p.setFileName("batch-" + i + ".jpg");
            photos.add(p);
        }
        photoRepo.saveAll(photos);

        List<ExifData> exifs = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            ExifData e = new ExifData();
            e.setPhoto(photos.get(i));
            e.setDateTaken(LocalDateTime.now().minusDays(i));
            exifs.add(e);
        }
        exifRepo.saveAll(exifs);

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear(); // 清掉 saveAll 阶段的计数
        em.clear();    // 清一级缓存，确保懒加载真实查库

        Page<Photo> page = photoRepo.findByIdIn(
                photos.stream().map(Photo::getId).toList(), Pageable.unpaged());
        for (Photo p : page.getContent()) {
            p.getExifData(); // PhotoResponse.from 的等价访问路径
        }

        long queries = stats.getQueryExecutionCount();
        // 主查询 1 + exifData 批量 2 批（20+5）≈ 3；若类级 @BatchSize 失效则 ≈ 27（每张 1 条）
        assertThat(queries).isLessThanOrEqualTo(5);
    }
}
