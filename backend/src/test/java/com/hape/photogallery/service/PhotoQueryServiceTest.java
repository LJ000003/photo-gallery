package com.hape.photogallery.service;

import com.hape.photogallery.config.MediaSignatureService;
import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.TimelineItem;
import com.hape.photogallery.entity.Category;
import com.hape.photogallery.entity.ExifData;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.ExifDataRepository;
import com.hape.photogallery.repository.PhotoRepository;
import com.hape.photogallery.service.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 照片查询侧服务测试（P2-#15 从 PhotoServiceTest 拆出）：
 * 列表/搜索（FULLTEXT 与 LIKE fallback）/详情/时间线/地图/DTO 转换。
 * 用例自原 PhotoServiceTest 原样搬移，不删改断言。
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class PhotoQueryServiceTest {

    @Mock private PhotoRepository photoRepo;
    @Mock private ExifDataRepository exifRepo;
    @Mock private ExifService exifService;
    @Mock private StorageService storage;
    @Mock private javax.sql.DataSource dataSource;
    @Mock private java.sql.Connection connection;
    @Mock private java.sql.DatabaseMetaData metaData;

    private static final MediaSignatureService MEDIA_SIGNATURE = new MediaSignatureService(
            "test-secret-0123456789abcdef0123456789abcdef", 300);

    @TempDir
    Path tempDir;

    private PhotoQueryService service;

    @BeforeEach
    void setUp() {
        // extractExifForPhoto 需要 uploadDir（文件不存在时返回 null 不落盘）
        when(storage.getUploadDir()).thenReturn(tempDir);
        // 默认 FullTextProbe(dataSource=null) → 按「支持 FULLTEXT」处理（保持 MySQL 语义）
        service = new PhotoQueryService(photoRepo, exifRepo, exifService, MEDIA_SIGNATURE, storage,
                new FullTextProbe(null));
    }

    /**
     * 构造 DataSource 报告指定数据库产品的查询服务（FULLTEXT 支持探测用）。
     */
    private PhotoQueryService serviceWithDbProduct(String productName) throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn(productName);
        return new PhotoQueryService(photoRepo, exifRepo, exifService, MEDIA_SIGNATURE, storage,
                new FullTextProbe(dataSource));
    }

    // ==================== listAll ====================

    @Test
    void listAll_shouldCallRepository() {
        Page<Photo> page = new PageImpl<>(List.of());
        when(photoRepo.findAll(any(PageRequest.class))).thenReturn(page);
        Page<Photo> result = service.listAll(null, null, PageRequest.of(0, 20));
        assertThat(result).isSameAs(page);
    }

    @Test
    void listAll_withTag_shouldCallFindByTagIds() {
        when(photoRepo.findByTagIds(any(), any())).thenReturn(new PageImpl<>(List.of()));
        service.listAll(List.of(1L), null, PageRequest.of(0, 20));
        verify(photoRepo).findByTagIds(List.of(1L), PageRequest.of(0, 20));
        verify(photoRepo, never()).findAll(any(PageRequest.class));
    }

    @Test
    void listAll_withCategory_shouldCallFindByCategoryIds() {
        when(photoRepo.findByCategoryIds(any(), any())).thenReturn(new PageImpl<>(List.of()));
        service.listAll(null, List.of(2L), PageRequest.of(0, 20));
        verify(photoRepo).findByCategoryIds(List.of(2L), PageRequest.of(0, 20));
    }

    @Test
    void listAll_withBoth_shouldCallCombinedQuery() {
        when(photoRepo.findByCategoryIdsAndTagIds(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        service.listAll(List.of(1L), List.of(2L), PageRequest.of(0, 20));
        verify(photoRepo).findByCategoryIdsAndTagIds(List.of(2L), List.of(1L), PageRequest.of(0, 20));
    }

    // ==================== search ====================

    @Test
    void search_shouldCallRepository() {
        when(photoRepo.search(eq("cat"), any())).thenReturn(new PageImpl<>(List.of()));
        service.search("cat", null, null, PageRequest.of(0, 20));
        verify(photoRepo).search("cat", PageRequest.of(0, 20));
    }

    @Test
    void search_withTagAndCategoryFilters_shouldCallCombinedQuery() {
        when(photoRepo.searchWithTagAndCategoryIds(eq("cat"), eq(List.of(1L)), eq(List.of(2L)), any()))
                .thenReturn(new PageImpl<>(List.of()));
        service.search("cat", List.of(1L), List.of(2L), PageRequest.of(0, 20));
        verify(photoRepo).searchWithTagAndCategoryIds("cat", List.of(1L), List.of(2L), PageRequest.of(0, 20));
    }

    @Test
    void search_withTagFilterOnly_shouldCallTagQuery() {
        when(photoRepo.searchWithTagIds(eq("cat"), eq(List.of(1L)), any()))
                .thenReturn(new PageImpl<>(List.of()));
        service.search("cat", List.of(1L), null, PageRequest.of(0, 20));
        verify(photoRepo).searchWithTagIds("cat", List.of(1L), PageRequest.of(0, 20));
    }

    @Test
    void search_withCategoryFilterOnly_shouldCallCategoryQuery() {
        when(photoRepo.searchWithCategoryIds(eq("cat"), eq(List.of(2L)), any()))
                .thenReturn(new PageImpl<>(List.of()));
        service.search("cat", null, List.of(2L), PageRequest.of(0, 20));
        verify(photoRepo).searchWithCategoryIds("cat", List.of(2L), PageRequest.of(0, 20));
    }

    @Test
    void search_withSort_shouldMapEntityPropertyToColumnName() {
        when(photoRepo.search(eq("cat"), any())).thenReturn(new PageImpl<>(List.of()));
        Pageable input = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt")));
        service.search("cat", null, null, input);
        verify(photoRepo).search(eq("cat"), argThat(p -> p.getSort().stream()
                .anyMatch(o -> o.getProperty().equals("created_at") && o.isDescending())));
    }

    @Test
    void search_singleChar_shouldUseLike() {
        when(photoRepo.searchByLike(eq("%海%"), any())).thenReturn(new PageImpl<>(List.of()));
        service.search("海", null, null, PageRequest.of(0, 20));
        verify(photoRepo).searchByLike("%海%", PageRequest.of(0, 20));
    }

    @Test
    void search_singleCharWithFilters_shouldUseLikeVariants() {
        when(photoRepo.searchByLikeWithTagIds(eq("%海%"), eq(List.of(1L)), any()))
                .thenReturn(new PageImpl<>(List.of()));
        service.search("海", List.of(1L), null, PageRequest.of(0, 20));
        verify(photoRepo).searchByLikeWithTagIds("%海%", List.of(1L), PageRequest.of(0, 20));

        when(photoRepo.searchByLikeWithCategoryIds(eq("%海%"), eq(List.of(2L)), any()))
                .thenReturn(new PageImpl<>(List.of()));
        service.search("海", null, List.of(2L), PageRequest.of(0, 20));
        verify(photoRepo).searchByLikeWithCategoryIds("%海%", List.of(2L), PageRequest.of(0, 20));

        when(photoRepo.searchByLikeWithTagAndCategoryIds(eq("%海%"), eq(List.of(1L)), eq(List.of(2L)), any()))
                .thenReturn(new PageImpl<>(List.of()));
        service.search("海", List.of(1L), List.of(2L), PageRequest.of(0, 20));
        verify(photoRepo).searchByLikeWithTagAndCategoryIds("%海%", List.of(1L), List.of(2L), PageRequest.of(0, 20));
    }

    @Test
    void search_singleChar_shouldEscapeLikeWildcards() {
        when(photoRepo.searchByLike(any(), any())).thenReturn(new PageImpl<>(List.of()));
        service.search("%", null, null, PageRequest.of(0, 20));
        verify(photoRepo).searchByLike("%\\%%", PageRequest.of(0, 20));
        service.search("_", null, null, PageRequest.of(0, 20));
        verify(photoRepo).searchByLike("%\\_%", PageRequest.of(0, 20));
    }

    @Test
    void search_twoChars_shouldUseFulltext() {
        when(photoRepo.search(eq("海边"), any())).thenReturn(new PageImpl<>(List.of()));
        service.search("海边", null, null, PageRequest.of(0, 20));
        verify(photoRepo).search("海边", PageRequest.of(0, 20));
    }

    // ==================== 非 MySQL 数据库退化为 LIKE（H2 无 MATCH...AGAINST，硬走 FULLTEXT 会 500） ====================

    @Test
    void search_onH2_shouldFallbackToLike() throws Exception {
        PhotoQueryService h2 = serviceWithDbProduct("H2");
        when(photoRepo.searchByLike(eq("%cat%"), any())).thenReturn(new PageImpl<>(List.of()));
        h2.search("cat", null, null, PageRequest.of(0, 20));
        verify(photoRepo).searchByLike("%cat%", PageRequest.of(0, 20));
        verify(photoRepo, never()).search(any(), any());
    }

    @Test
    void search_onH2_withFilters_shouldUseLikeVariants() throws Exception {
        PhotoQueryService h2 = serviceWithDbProduct("H2");
        when(photoRepo.searchByLikeWithTagIds(eq("%cat%"), eq(List.of(1L)), any()))
                .thenReturn(new PageImpl<>(List.of()));
        h2.search("cat", List.of(1L), null, PageRequest.of(0, 20));
        verify(photoRepo).searchByLikeWithTagIds("%cat%", List.of(1L), PageRequest.of(0, 20));

        when(photoRepo.searchByLikeWithCategoryIds(eq("%cat%"), eq(List.of(2L)), any()))
                .thenReturn(new PageImpl<>(List.of()));
        h2.search("cat", null, List.of(2L), PageRequest.of(0, 20));
        verify(photoRepo).searchByLikeWithCategoryIds("%cat%", List.of(2L), PageRequest.of(0, 20));

        when(photoRepo.searchByLikeWithTagAndCategoryIds(eq("%cat%"), eq(List.of(1L)), eq(List.of(2L)), any()))
                .thenReturn(new PageImpl<>(List.of()));
        h2.search("cat", List.of(1L), List.of(2L), PageRequest.of(0, 20));
        verify(photoRepo).searchByLikeWithTagAndCategoryIds("%cat%", List.of(1L), List.of(2L), PageRequest.of(0, 20));
    }

    @Test
    void search_onMySQL_shouldKeepFulltext() throws Exception {
        PhotoQueryService mysql = serviceWithDbProduct("MySQL");
        when(photoRepo.search(eq("cat"), any())).thenReturn(new PageImpl<>(List.of()));
        mysql.search("cat", null, null, PageRequest.of(0, 20));
        verify(photoRepo).search("cat", PageRequest.of(0, 20));
        verify(photoRepo, never()).searchByLike(any(), any());
    }

    // ==================== FULLTEXT 运算符剥离（P0-3） ====================

    @Test
    void search_unpairedQuote_shouldStripOperator() {
        when(photoRepo.search(eq("ab"), any())).thenReturn(new PageImpl<>(List.of()));
        service.search("ab\"", null, null, PageRequest.of(0, 20));
        verify(photoRepo).search("ab", PageRequest.of(0, 20));
    }

    @Test
    void search_minusOperator_shouldNotBeExclusionSemantics() {
        when(photoRepo.search(eq("secret"), any())).thenReturn(new PageImpl<>(List.of()));
        service.search("-secret", null, null, PageRequest.of(0, 20));
        verify(photoRepo).search("secret", PageRequest.of(0, 20));
    }

    @Test
    void search_strippedToSingleChar_shouldFallbackToLike() {
        when(photoRepo.searchByLike(eq("%a%"), any())).thenReturn(new PageImpl<>(List.of()));
        service.search("a\"", null, null, PageRequest.of(0, 20));
        verify(photoRepo).searchByLike("%a%", PageRequest.of(0, 20));
    }

    @Test
    void search_allOperatorsStripped_shouldReturnEmptyPage() {
        Page<Photo> result = service.search("\"***\"", null, null, PageRequest.of(0, 20));
        assertThat(result).isEmpty();
        verify(photoRepo, never()).search(any(), any());
    }

    @Test
    void sanitizeFullText_shouldStripBooleanOperators() {
        assertThat(PhotoQueryService.sanitizeFullText("海边 (1) +晴天")).isEqualTo("海边 1 晴天");
        assertThat(PhotoQueryService.sanitizeFullText("\"phrase\" @x ~y")).isEqualTo("phrase x y");
    }

    // ==================== ORDER BY 白名单（P0-2） ====================

    @Test
    void search_sortBySqlInjection_shouldReject400() {
        Pageable malicious = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("(SELECT SLEEP(5))")));
        assertThatThrownBy(() -> service.search("cat", null, null, malicious))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的排序字段");
    }

    @Test
    void search_sortByUnknownProperty_shouldReject400() {
        Pageable malicious = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("id")));
        assertThatThrownBy(() -> service.search("cat", null, null, malicious))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的排序字段");
    }

    @Test
    void search_sortByName_shouldMapToNameColumn() {
        when(photoRepo.search(eq("cat"), any())).thenReturn(new PageImpl<>(List.of()));
        Pageable input = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("name")));
        service.search("cat", null, null, input);
        verify(photoRepo).search(eq("cat"), argThat(p -> p.getSort().stream()
                .anyMatch(o -> o.getProperty().equals("name") && o.isAscending())));
    }

    @Test
    void search_blankQuery_shouldFallbackToFindAll() {
        when(photoRepo.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));
        service.search("  ", null, null, PageRequest.of(0, 20));
        verify(photoRepo).findAll(any(PageRequest.class));
    }

    @Test
    void search_nullQuery_shouldFallbackToFindAll() {
        when(photoRepo.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));
        service.search(null, null, null, PageRequest.of(0, 20));
        verify(photoRepo).findAll(any(PageRequest.class));
    }

    // ==================== getById ====================

    @Test
    void getById_found_shouldReturnPhoto() {
        Photo p = new Photo(); p.setId(1L); p.setName("test");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        assertThat(service.getById(1L).getName()).isEqualTo("test");
    }

    @Test
    void getById_notFound_shouldThrow() {
        when(photoRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(99L)).hasMessageContaining("不存在");
    }

    // ==================== findByIds ====================

    @Test
    void findByIds_shouldCallRepository() {
        when(photoRepo.findByIdIn(any(), any())).thenReturn(new PageImpl<>(List.of()));
        service.findByIds(List.of(1L, 2L), PageRequest.of(0, 20));
        verify(photoRepo).findByIdIn(List.of(1L, 2L), PageRequest.of(0, 20));
    }

    // ==================== getTimeline ====================

    @Test
    void getTimeline_desc_shouldReturnMappedItems() {
        ExifData e = new ExifData();
        e.setId(1L);
        Photo p = new Photo(); p.setId(10L); p.setName("p1");
        e.setPhoto(p);
        e.setDateTaken(LocalDateTime.of(2026, 1, 15, 10, 0));
        e.setCameraModel("Canon");
        PageRequest pr = PageRequest.of(0, 50);
        when(exifRepo.findWithDateTakenAndPhotoDesc(pr)).thenReturn(new PageImpl<>(List.of(e)));

        Page<TimelineItem> page = service.getTimeline("desc", pr);
        List<TimelineItem> items = page.getContent();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPhotoId()).isEqualTo(10L);
        assertThat(items.get(0).getCameraModel()).isEqualTo("Canon");
    }

    @Test
    void getTimeline_asc_shouldCallAsc() {
        PageRequest pr = PageRequest.of(0, 50);
        when(exifRepo.findWithDateTakenAndPhotoAsc(pr)).thenReturn(new PageImpl<>(List.of()));
        service.getTimeline("asc", pr);
        verify(exifRepo).findWithDateTakenAndPhotoAsc(pr);
    }

    // ==================== getMapPhotos ====================

    @Test
    void getMapPhotos_shouldTransformCoordinates() {
        ExifData e = new ExifData();
        e.setLatitude(39.9); e.setLongitude(116.4);
        Photo p = new Photo(); p.setId(10L); p.setName("map1");
        e.setPhoto(p);
        PageRequest pr = PageRequest.of(0, 500);
        when(exifRepo.findWithGpsInBounds(eq(30.0), eq(100.0), eq(50.0), eq(130.0), eq(pr)))
                .thenReturn(List.of(e));

        List<MapItem> items = service.getMapPhotos(30.0, 100.0, 50.0, 130.0);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPhotoId()).isEqualTo(10L);
        // coordinates should have been transformed from WGS84 to GCJ02
        assertThat(items.get(0).getLatitude()).isNotEqualTo(39.9);
        assertThat(items.get(0).getLongitude()).isNotEqualTo(116.4);
    }

    @Test
    void getMapPhotos_shouldIncludeMediaToken() {
        // 回归：内联 MapItem.from 会漏掉短时签名，前端 popup 缩略图 401
        ExifData e = new ExifData();
        e.setLatitude(39.9); e.setLongitude(116.4);
        Photo p = new Photo(); p.setId(10L); p.setName("map1");
        e.setPhoto(p);
        when(exifRepo.findWithGpsInBounds(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(PageRequest.class))).thenReturn(List.of(e));

        List<MapItem> items = service.getMapPhotos(30.0, 100.0, 50.0, 130.0);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getMediaToken()).isNotBlank();
        // 签名能通过校验且绑定 photoId
        long verified = MEDIA_SIGNATURE.verify(items.get(0).getMediaToken());
        assertThat(verified).isEqualTo(10L);
    }

    // ==================== extractExif ====================
    // extractExifForExisting（批量）随迁移方法移至 MigrationService，测试见 MigrationServiceTest

    @Test
    void extractExifForPhoto_notFound_shouldReturnNull() {
        Photo p = new Photo(); p.setId(1L); p.setName("p");
        p.setFileName("nonexistent.jpg");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        assertThat(service.extractExifForPhoto(1L)).isNull();
    }

    // ==================== DTO conversion ====================

    @Test
    void toResponse_shouldMapAllFields() {
        Photo p = new Photo(); p.setId(1L); p.setName("p1");
        p.setDescription("desc"); p.setFileSize(100L);
        p.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        Category c = new Category("cat"); c.setId(1L);
        p.setCategory(c);

        PhotoResponse r = service.toResponse(p);
        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getName()).isEqualTo("p1");
        assertThat(r.getDescription()).isEqualTo("desc");
        assertThat(r.getFileSize()).isEqualTo(100L);
        assertThat(r.getCategory().getId()).isEqualTo(1L);
    }
}
