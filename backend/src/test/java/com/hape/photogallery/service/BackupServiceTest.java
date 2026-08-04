package com.hape.photogallery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hape.photogallery.dto.BackupExportRequest;
import com.hape.photogallery.entity.Album;
import com.hape.photogallery.entity.Category;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.Tag;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.AlbumRepository;
import com.hape.photogallery.repository.CategoryRepository;
import com.hape.photogallery.repository.PhotoRepository;
import com.hape.photogallery.repository.TagRepository;
import com.hape.photogallery.service.BackupService.BackupBundle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 备份导出集成测试：真实 repository（H2）+ 真实 LocalStorageService（临时目录）。
 * collect() 事务内懒加载初始化 + findForBackup 筛选；writeTo() 产出可解包的 zip。
 */
@DataJpaTest
class BackupServiceTest {

    @Autowired private PhotoRepository photoRepo;
    @Autowired private TagRepository tagRepo;
    @Autowired private CategoryRepository catRepo;
    @Autowired private AlbumRepository albumRepo;

    @TempDir
    Path tempDir;

    private BackupService service;
    private Photo p1, p2;

    @BeforeEach
    void setUp() throws IOException {
        LocalStorageService storage = new LocalStorageService(tempDir.toString());
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new BackupService(photoRepo, tagRepo, catRepo, albumRepo, storage, mapper,
                tempDir.resolve("backup-cache").toString());

        // 真实文件 + 元数据：p1 带分类/标签/相册，p2 无关联
        Path dir = Files.createDirectories(tempDir.resolve("2024/01"));
        Files.write(dir.resolve("uuid_a.jpg"), new byte[]{1, 2, 3, 4, 5});
        Files.write(dir.resolve("uuid_b.jpg"), new byte[]{9, 9, 9});

        Category cat = catRepo.save(new Category("风景"));
        Tag tag = tagRepo.save(new Tag("日出", "#ff8800"));
        Album album = new Album("旅行");
        albumRepo.save(album);

        p1 = new Photo();
        p1.setName("照片A");
        p1.setDescription("描述A");
        p1.setFileName("2024/01/uuid_a.jpg");
        p1.setOriginalFileName("a.jpg");
        p1.setFileSize(5L);
        p1.setContentType("image/jpeg");
        p1.setCreatedAt(LocalDateTime.of(2026, 7, 15, 10, 0));
        p1.setProcessingStatus("DONE");
        p1.setFileHash("abc123");
        p1.setCategory(cat);
        p1.getTags().add(tag);
        photoRepo.save(p1);

        // Photo.albums 是 mappedBy（inverse side），join 表由 Album.photos 管理，
        // 必须双向关联（与 PhotoRepositoryTest 一致），否则 join 表不会写入
        album.getPhotos().add(p1);
        p1.getAlbums().add(album);
        albumRepo.save(album);
        photoRepo.save(p1);

        p2 = new Photo();
        p2.setName("照片B");
        p2.setFileName("2024/01/uuid_b.jpg");
        p2.setCreatedAt(LocalDateTime.of(2026, 7, 20, 12, 0));
        p2.setProcessingStatus("DONE");
        photoRepo.save(p2);
    }

    private Map<String, byte[]> unpack(byte[] zipBytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    // ==================== collect ====================

    @Test
    void collect_shouldCollectPhotosWithRelations() {
        BackupBundle bundle = service.collect(new BackupExportRequest());

        assertThat(bundle.photos()).hasSize(2);
        var record = bundle.photos().stream().filter(p -> p.id().equals(p1.getId())).findFirst().orElseThrow();
        assertThat(record.name()).isEqualTo("照片A");
        assertThat(record.fileName()).isEqualTo("2024/01/uuid_a.jpg");
        assertThat(record.categoryId()).isEqualTo(p1.getCategory().getId());
        assertThat(record.tagIds()).containsExactly(p1.getTags().iterator().next().getId());
        assertThat(record.albumIds()).containsExactly(p1.getAlbums().iterator().next().getId());

        // 全局元数据（tags/categories/albums）全量导出，不受照片筛选影响
        assertThat(bundle.tags()).hasSize(1);
        assertThat(bundle.categories()).hasSize(1);
        assertThat(bundle.albums()).hasSize(1);
        assertThat(bundle.filters()).isNotNull();
    }

    @Test
    void collect_emptyResult_shouldThrow400() {
        BackupExportRequest req = new BackupExportRequest();
        req.setCategoryId(9999L);

        assertThatThrownBy(() -> service.collect(req))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有符合条件的照片");
    }

    @Test
    void collect_invalidDate_shouldThrow400() {
        BackupExportRequest req = new BackupExportRequest();
        req.setDateFrom("2026-13-01");

        assertThatThrownBy(() -> service.collect(req))
                .isInstanceOf(BusinessException.class)
                .hasMessage("日期格式错误，应为 yyyy-MM-dd");
    }

    @Test
    void collect_filterByCategoryAndDate_shouldFilter() {
        BackupExportRequest req = new BackupExportRequest();
        req.setDateFrom("2026-07-01");
        req.setDateTo("2026-07-18"); // 覆盖 p1，不含 p2（7-20）

        BackupBundle bundle = service.collect(req);
        assertThat(bundle.photos()).hasSize(1);
        assertThat(bundle.photos().get(0).id()).isEqualTo(p1.getId());
    }

    @Test
    void collect_albumZero_shouldReturnUnassignedOnly() {
        BackupExportRequest req = new BackupExportRequest();
        req.setAlbumId(0L);

        BackupBundle bundle = service.collect(req);
        assertThat(bundle.photos()).hasSize(1);
        assertThat(bundle.photos().get(0).id()).isEqualTo(p2.getId());
    }

    // ==================== writeTo ====================

    @Test
    void writeTo_shouldProduceZipWithMetadataAndFiles() throws IOException {
        BackupBundle bundle = service.collect(new BackupExportRequest());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeTo(out, bundle);

        Map<String, byte[]> entries = unpack(out.toByteArray());
        assertThat(entries.keySet())
                .contains("database/metadata.json", "database/photos.json", "database/exif.json",
                        "database/tags.json", "database/categories.json", "database/albums.json",
                        "photos/2024/01/uuid_a.jpg", "photos/2024/01/uuid_b.jpg");
        assertThat(entries.get("photos/2024/01/uuid_a.jpg")).containsExactly(1, 2, 3, 4, 5);

        // metadata.json 记录照片数与导出版本
        String metadata = new String(entries.get("database/metadata.json"));
        assertThat(metadata).contains("\"photoCount\":2");
        assertThat(metadata).contains("\"version\":\"1.0\"");
        // photos.json 含懒加载关系 id（说明事务内初始化成功）
        String photosJson = new String(entries.get("database/photos.json"));
        assertThat(photosJson).contains("\"categoryId\"");
        assertThat(photosJson).contains("\"tagIds\"");
    }

    @Test
    void writeTo_missingFile_shouldSkipAndStillWriteMetadata() throws IOException {
        BackupBundle bundle = service.collect(new BackupExportRequest());
        Files.delete(tempDir.resolve("2024/01/uuid_a.jpg"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeTo(out, bundle);

        Map<String, byte[]> entries = unpack(out.toByteArray());
        assertThat(entries).containsKey("database/photos.json");
        assertThat(entries).doesNotContainKey("photos/2024/01/uuid_a.jpg");
        assertThat(entries).containsKey("photos/2024/01/uuid_b.jpg");
    }

    @Test
    void writeTo_metadataJson_shouldContainFilters() throws IOException {
        BackupExportRequest req = new BackupExportRequest();
        req.setCategoryId(p1.getCategory().getId());
        BackupBundle bundle = service.collect(req);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeTo(out, bundle);

        Map<String, byte[]> entries = unpack(out.toByteArray());
        String metadata = new String(entries.get("database/metadata.json"));
        assertThat(metadata).contains("\"categoryId\":" + p1.getCategory().getId());
    }

    // ==================== 预生成缓存 ====================

    @Test
    void isCacheFresh_withoutCache_shouldBeFalse() {
        assertThat(service.isCacheFresh()).isFalse();
    }

    @Test
    void updateCache_shouldMakeCacheFreshAndWritable() throws IOException {
        BackupBundle bundle = service.collect(new BackupExportRequest());
        service.updateCache(bundle);

        // 指纹一致 → 缓存新鲜
        assertThat(service.isCacheFresh()).isTrue();
        // 缓存文件真实存在且可解包
        assertThat(Files.isRegularFile(service.getCacheFile())).isTrue();
        Map<String, byte[]> entries = unpack(Files.readAllBytes(service.getCacheFile()));
        assertThat(entries).containsKey("database/photos.json");
        assertThat(entries).containsKey("photos/2024/01/uuid_a.jpg");
    }

    @Test
    void updateCache_thenAddPhoto_shouldNotBeFresh() throws IOException {
        BackupBundle bundle = service.collect(new BackupExportRequest());
        service.updateCache(bundle);
        assertThat(service.isCacheFresh()).isTrue();

        // 新照片上传 → 指纹变化 → 缓存过期
        Photo p3 = new Photo();
        p3.setName("照片C");
        p3.setFileName("2024/01/uuid_c.jpg");
        p3.setCreatedAt(LocalDateTime.now());
        p3.setProcessingStatus("DONE");
        photoRepo.save(p3);

        assertThat(service.isCacheFresh()).isFalse();
    }

    @Test
    void generateCachedBackup_shouldCreateCacheFile() throws IOException {
        assertThat(service.generateCachedBackup()).isTrue();
        assertThat(Files.isRegularFile(service.getCacheFile())).isTrue();
        assertThat(service.isCacheFresh()).isTrue();
    }
}
