package com.hape.photogallery.service;

import com.hape.photogallery.dto.BatchPhotoUpdateRequest;
import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.PhotoUpdateRequest;
import com.hape.photogallery.dto.TimelineItem;
import com.hape.photogallery.dto.UploadParams;
import com.hape.photogallery.entity.Category;
import com.hape.photogallery.entity.ExifData;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.ProcessingStatus;
import com.hape.photogallery.entity.Tag;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.exception.DuplicateException;
import com.hape.photogallery.exception.FileSizeExceededException;
import com.hape.photogallery.messaging.ProcessingMessageSender;
import com.hape.photogallery.repository.CategoryRepository;
import com.hape.photogallery.repository.ExifDataRepository;
import com.hape.photogallery.repository.PhotoRepository;
import com.hape.photogallery.repository.TagRepository;
import com.hape.photogallery.service.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class PhotoServiceTest {

    @Mock private PhotoRepository photoRepo;
    @Mock private TagRepository tagRepo;
    @Mock private CategoryRepository catRepo;
    @Mock private ExifDataRepository exifRepo;
    @Mock private ExifService exifService;
    @Mock private ImageProcessingService imageService;
    @Mock private AlbumService albumService;
    @Mock private StorageService storage;
    @Mock private ProcessingMessageSender processingSender;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private javax.sql.DataSource dataSource;
    @Mock private java.sql.Connection connection;
    @Mock private java.sql.DatabaseMetaData metaData;

    @TempDir Path tempDir;

    private PhotoService service;

    // minimal valid JPEG header
    private static final byte[] JPEG_BYTES = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
        0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };

    @BeforeEach
    void setUp() throws IOException {
        when(storage.getUploadDir()).thenReturn(tempDir);
        when(storage.resolveSafe(any())).thenAnswer(inv -> {
            String path = inv.getArgument(0);
            return tempDir.resolve(path).normalize();
        });
        doAnswer(inv -> {
            java.nio.file.Files.copy(
                ((MultipartFile) inv.getArgument(0)).getInputStream(),
                (Path) inv.getArgument(1));
            return null;
        }).when(storage).store(any(MultipartFile.class), any(Path.class));
        doAnswer(inv -> {
            java.nio.file.Files.createDirectories(inv.getArgument(0));
            return null;
        }).when(storage).createDirectories(any(Path.class));
        doAnswer(inv -> {
            java.nio.file.Files.deleteIfExists(tempDir.resolve((String) inv.getArgument(0)));
            return null;
        }).when(storage).deleteFile(any());

        // upload/batchUpload/transform 走 transactionTemplate：桩执行真实回调，
        // mock TransactionStatus 防止回调内 setRollbackOnly()/isRollbackOnly() 对 null NPE（P4-#46）
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });

        service = new PhotoService(photoRepo, tagRepo, catRepo, exifRepo, exifService,
                imageService, albumService, storage, processingSender, transactionTemplate,
                new com.hape.photogallery.config.MediaSignatureService(
                        "test-secret-0123456789abcdef0123456789abcdef", 300),
                new FilePathResolver(photoRepo, storage), null);
    }

    /**
     * 构造 DataSource 报告指定数据库产品的 service（FULLTEXT 支持探测用）。
     * 默认 service 的 dataSource 为 null → 按「支持 FULLTEXT」处理（保持 MySQL 语义）。
     */
    private PhotoService serviceWithDbProduct(String productName) throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn(productName);
        return new PhotoService(photoRepo, tagRepo, catRepo, exifRepo, exifService,
                imageService, albumService, storage, processingSender, transactionTemplate,
                new com.hape.photogallery.config.MediaSignatureService(
                        "test-secret-0123456789abcdef0123456789abcdef", 300),
                new FilePathResolver(photoRepo, storage), dataSource);
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
        PhotoService h2 = serviceWithDbProduct("H2");
        when(photoRepo.searchByLike(eq("%cat%"), any())).thenReturn(new PageImpl<>(List.of()));
        h2.search("cat", null, null, PageRequest.of(0, 20));
        verify(photoRepo).searchByLike("%cat%", PageRequest.of(0, 20));
        verify(photoRepo, never()).search(any(), any());
    }

    @Test
    void search_onH2_withFilters_shouldUseLikeVariants() throws Exception {
        PhotoService h2 = serviceWithDbProduct("H2");
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
        PhotoService mysql = serviceWithDbProduct("MySQL");
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
        assertThat(PhotoService.sanitizeFullText("海边 (1) +晴天")).isEqualTo("海边 1 晴天");
        assertThat(PhotoService.sanitizeFullText("\"phrase\" @x ~y")).isEqualTo("phrase x y");
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

    // ==================== upload ====================

    @Test
    void upload_shouldSavePhotoAndTriggerAsyncProcessing() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", JPEG_BYTES);
        when(photoRepo.save(any(Photo.class))).thenAnswer(inv -> {
            Photo p = inv.getArgument(0);
            if (p.getId() == null) { p.setId(1L); p.setFileName("2026/07/test.jpg"); }
            return p;
        });
        when(tagRepo.findAllById(any())).thenReturn(List.of());

        Photo result = service.upload(file, new UploadParams("test", "desc", List.of(1L), 5L, "watermark"));

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("test");
        assertThat(result.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSING);
        verify(photoRepo).save(any());
        verify(imageService).validateImageMagicBytes(any());
        // 图片处理已移至异步执行
        verify(processingSender).send(any(Long.class), any(Path.class), any(), any(), eq("watermark"));
    }

    @Test
    void upload_fileTooLarge_shouldThrow() {
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", new byte[11 * 1024 * 1024]);
        assertThatThrownBy(() -> service.upload(file, new UploadParams("big", null, null, null, null)))
                .isInstanceOf(FileSizeExceededException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    void upload_traversalFileName_shouldSanitizeBeforeStore() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "../../evil.jpg", "image/jpeg", JPEG_BYTES);
        when(photoRepo.save(any(Photo.class))).thenAnswer(inv -> {
            Photo p = inv.getArgument(0);
            if (p.getId() == null) { p.setId(1L); p.setFileName("2026/07/test.jpg"); }
            return p;
        });
        when(tagRepo.findAllById(any())).thenReturn(List.of());

        Photo result = service.upload(file, new UploadParams("test", null, null, null, null));

        // 存储路径必须被消毒：不含 .. 且落在上传目录内
        verify(storage).store(any(MultipartFile.class), argThat(target -> {
            assertThat(target.toString()).doesNotContain("..");
            return target.startsWith(tempDir);
        }));
        assertThat(result.getFileName()).doesNotContain("..");
    }

    @Test
    void sanitizeFileName_shouldStripTraversalAndSeparators() {
        assertThat(PhotoService.sanitizeFileName("../../evil.jpg")).doesNotContain("..").doesNotContain("/");
        assertThat(PhotoService.sanitizeFileName("a/b\\c.txt")).isEqualTo("a_b_c.txt");
        assertThat(PhotoService.sanitizeFileName("中文 照片 (1).jpg")).isEqualTo("中文 照片 _1_.jpg");
        assertThat(PhotoService.sanitizeFileName(null)).isEmpty();
        assertThat(PhotoService.sanitizeFileName("  ")).isEmpty();
        assertThat(PhotoService.sanitizeFileName("a".repeat(150))).hasSize(100);
    }

    @Test
    void upload_concurrentDuplicate_shouldReturnExistingPhotoAndCleanupFile() throws IOException {
        // 并发同 hash 上传：check-then-insert 竞态撞唯一索引 → 删残留文件 + 重查返回 DuplicateException（P4-#42）
        // 第一次调用是事务内查重（返回空、正常进入插入），save 抛唯一索引冲突后外层重查返回已有照片
        MockMultipartFile file = new MockMultipartFile("file", "dup.jpg", "image/jpeg", JPEG_BYTES);
        when(photoRepo.save(any(Photo.class))).thenThrow(new DataIntegrityViolationException("dup key"));
        Photo existing = new Photo();
        existing.setId(99L);
        existing.setName("existing");
        existing.setProcessingStatus(ProcessingStatus.DONE);
        when(photoRepo.findWithDetailsByFileHash(any()))
                .thenReturn(Optional.empty(), Optional.of(existing));

        assertThatThrownBy(() -> service.upload(file, new UploadParams("test", null, null, null, null)))
                .isInstanceOf(DuplicateException.class)
                .satisfies(e -> assertThat(((DuplicateException) e).getExisting().getId()).isEqualTo(99L));
        // 竞态失败路径必须清理已落盘文件，避免孤儿文件（内层 catch + 外层安全网共 2 次，幂等）
        verify(storage, atLeastOnce()).deleteFile(any());
    }

    @Test
    void upload_concurrentDuplicate_requeryMiss_shouldRethrow() throws IOException {
        // 唯一索引冲突但重查无结果（理论不可能，防御性）：原样抛出，不吞异常
        MockMultipartFile file = new MockMultipartFile("file", "dup.jpg", "image/jpeg", JPEG_BYTES);
        when(photoRepo.save(any(Photo.class))).thenThrow(new DataIntegrityViolationException("dup key"));
        when(photoRepo.findWithDetailsByFileHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(file, new UploadParams("test", null, null, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void upload_emptyName_shouldUseOriginalFileName() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "original.jpg", "image/jpeg", JPEG_BYTES);
        when(photoRepo.save(any(Photo.class))).thenAnswer(inv -> {
            Photo p = inv.getArgument(0);
            if (p.getId() == null) { p.setId(1L); p.setFileName("2026/07/test.jpg"); }
            return p;
        });
        when(exifService.extractAndSave(any(), any())).thenReturn(null);

        Photo result = service.upload(file, new UploadParams(null, null, null, null, null));
        assertThat(result.getName()).isEqualTo("original.jpg");
    }

    // ==================== batchUpload ====================

    @Test
    void batchUpload_shouldUploadEachFile() throws IOException {
        MockMultipartFile f1 = new MockMultipartFile("files", "a.jpg", "image/jpeg", JPEG_BYTES);
        MockMultipartFile f2 = new MockMultipartFile("files", "b.jpg", "image/jpeg", JPEG_BYTES);
        when(photoRepo.save(any(Photo.class))).thenAnswer(inv -> {
            Photo p = inv.getArgument(0); p.setId(1L); return p;
        });
        when(exifService.extractAndSave(any(), any())).thenReturn(null);

        List<Photo> results = service.batchUpload(List.of(f1, f2), new UploadParams("batch", null, null, null, null));
        assertThat(results).hasSize(2);
    }

    // ==================== update ====================

    @Test
    void update_shouldModifyFields() {
        Photo p = new Photo(); p.setId(1L); p.setName("old");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(photoRepo.save(any())).thenReturn(p);

        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setName("newName");
        req.setDescription("newDesc");

        PhotoResponse result = service.update(1L, req);
        assertThat(result.getName()).isEqualTo("newName");
        assertThat(result.getDescription()).isEqualTo("newDesc");
    }

    @Test
    void update_withTags_shouldSetTags() {
        Photo p = new Photo(); p.setId(1L); p.setName("p");
        Tag t = new Tag("tag", "#fff"); t.setId(1L);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(photoRepo.save(any())).thenReturn(p);
        when(tagRepo.findAllById(List.of(1L))).thenReturn(List.of(t));

        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setName("p");
        req.setTagIds(List.of(1L));

        PhotoResponse result = service.update(1L, req);
        assertThat(result.getTags()).hasSize(1);
    }

    @Test
    void update_nullCategory_shouldKeepCategory() {
        Photo p = new Photo(); p.setId(1L); p.setName("p");
        Category c = new Category("cat"); c.setId(5L);
        p.setCategory(c);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(photoRepo.save(any())).thenReturn(p);

        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setName("p");
        req.setCategoryId(null); // null = 不修改分类（防部分更新静默清空）

        PhotoResponse result = service.update(1L, req);
        assertThat(result.getCategory()).isNotNull();
        assertThat(result.getCategory().getId()).isEqualTo(5L);
    }

    @Test
    void update_zeroCategory_shouldClearCategory() {
        Photo p = new Photo(); p.setId(1L); p.setName("p");
        Category c = new Category("cat"); c.setId(5L);
        p.setCategory(c);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(photoRepo.save(any())).thenReturn(p);

        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setName("p");
        req.setCategoryId(0L); // 0 = 清除分类（与 albumId=0 的"未分配"约定一致）

        PhotoResponse result = service.update(1L, req);
        assertThat(result.getCategory()).isNull();
    }

    @Test
    void update_nonexistentCategory_shouldThrow404() {
        Photo p = new Photo(); p.setId(1L); p.setName("p");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(catRepo.findById(99L)).thenReturn(Optional.empty());

        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setName("p");
        req.setCategoryId(99L);

        assertThatThrownBy(() -> service.update(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类不存在");
        verify(photoRepo, never()).save(any());
    }

    // ==================== transform（P4-#37 已拆至 PhotoTransformService，测试见 PhotoTransformServiceTest） ====================

    // ==================== delete ====================

    @Test
    void delete_shouldSoftDelete() throws IOException {
        Path filePath = tempDir.resolve("2026/07/test.jpg");
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, JPEG_BYTES);

        Photo p = new Photo(); p.setId(1L); p.setName("test");
        p.setFileName("2026/07/test.jpg");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));

        service.delete(1L);

        verify(photoRepo).save(p);
        assertThat(p.getDeletedAt()).isNotNull();
        assertThat(Files.exists(filePath)).isTrue(); // 软删除不删文件
    }

    @Test
    void delete_shouldNotTouchExif() {
        Photo p = new Photo(); p.setId(1L); p.setName("test");
        p.setFileName("2026/07/test.jpg");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));

        service.delete(1L);

        verify(exifRepo, never()).delete(any());
        verify(photoRepo).save(p);
        assertThat(p.getDeletedAt()).isNotNull();
    }

    // ==================== batchDelete ====================

    @Test
    void batchDelete_shouldReturnCount() {
        Photo p1 = new Photo(); p1.setId(1L); p1.setName("a"); p1.setFileName("2026/07/a.jpg");
        Photo p2 = new Photo(); p2.setId(2L); p2.setName("b"); p2.setFileName("2026/07/b.jpg");
        when(photoRepo.findAllById(List.of(1L, 2L))).thenReturn(List.of(p1, p2));

        int count = service.batchDelete(List.of(1L, 2L));
        assertThat(count).isEqualTo(2);
    }

    @Test
    void batchDelete_skipMissing() {
        when(photoRepo.findAllById(List.of(1L))).thenReturn(List.of());

        int count = service.batchDelete(List.of(1L));
        assertThat(count).isEqualTo(0);
    }

    // ==================== batchUpdate ====================

    private BatchPhotoUpdateRequest batchReq(List<Long> photoIds) {
        BatchPhotoUpdateRequest req = new BatchPhotoUpdateRequest();
        req.setPhotoIds(photoIds);
        return req;
    }

    @Test
    void batchUpdate_addTags_shouldAddToAllPhotos() {
        Photo p1 = new Photo(); p1.setId(1L); p1.setName("a");
        Photo p2 = new Photo(); p2.setId(2L); p2.setName("b");
        Tag t = new Tag("旅行", "#00d4ff"); t.setId(10L);
        when(photoRepo.findAllById(List.of(1L, 2L))).thenReturn(List.of(p1, p2));
        when(tagRepo.findAllById(List.of(10L))).thenReturn(List.of(t));

        BatchPhotoUpdateRequest req = batchReq(List.of(1L, 2L));
        req.setAddTagIds(List.of(10L));

        List<PhotoResponse> result = service.batchUpdate(req);
        assertThat(result).hasSize(2);
        assertThat(p1.getTags()).extracting(Tag::getId).contains(10L);
        assertThat(p2.getTags()).extracting(Tag::getId).contains(10L);
        verify(photoRepo).saveAll(any());
    }

    @Test
    void batchUpdate_removeTags_shouldRemoveFromAllPhotos() {
        Photo p1 = new Photo(); p1.setId(1L); p1.setName("a");
        Tag t1 = new Tag("旅行", "#fff"); t1.setId(1L);
        Tag t2 = new Tag("美食", "#fff"); t2.setId(2L);
        p1.setTags(new HashSet<>(List.of(t1, t2)));
        when(photoRepo.findAllById(List.of(1L))).thenReturn(List.of(p1));

        BatchPhotoUpdateRequest req = batchReq(List.of(1L));
        req.setRemoveTagIds(List.of(1L));

        service.batchUpdate(req);
        assertThat(p1.getTags()).extracting(Tag::getId).containsExactly(2L);
    }

    @Test
    void batchUpdate_overlap_addWins() {
        Photo p1 = new Photo(); p1.setId(1L); p1.setName("a");
        Tag t = new Tag("旅行", "#fff"); t.setId(1L);
        p1.setTags(new HashSet<>(List.of(t)));
        when(photoRepo.findAllById(List.of(1L))).thenReturn(List.of(p1));
        when(tagRepo.findAllById(List.of(1L))).thenReturn(List.of(t));

        BatchPhotoUpdateRequest req = batchReq(List.of(1L));
        req.setRemoveTagIds(List.of(1L));
        req.setAddTagIds(List.of(1L));

        service.batchUpdate(req);
        assertThat(p1.getTags()).extracting(Tag::getId).containsExactly(1L);
    }

    @Test
    void batchUpdate_categorySet_shouldSetCategory() {
        Photo p1 = new Photo(); p1.setId(1L); p1.setName("a");
        Category c = new Category("cat"); c.setId(5L);
        when(photoRepo.findAllById(List.of(1L))).thenReturn(List.of(p1));
        when(catRepo.findById(5L)).thenReturn(Optional.of(c));

        BatchPhotoUpdateRequest req = batchReq(List.of(1L));
        req.setCategoryOp(BatchPhotoUpdateRequest.CategoryOp.SET);
        req.setCategoryId(5L);

        service.batchUpdate(req);
        assertThat(p1.getCategory()).isSameAs(c);
    }

    @Test
    void batchUpdate_categoryClear_shouldClearCategory() {
        Photo p1 = new Photo(); p1.setId(1L); p1.setName("a");
        Category c = new Category("cat"); c.setId(5L);
        p1.setCategory(c);
        when(photoRepo.findAllById(List.of(1L))).thenReturn(List.of(p1));

        BatchPhotoUpdateRequest req = batchReq(List.of(1L));
        req.setCategoryOp(BatchPhotoUpdateRequest.CategoryOp.CLEAR);

        service.batchUpdate(req);
        assertThat(p1.getCategory()).isNull();
    }

    @Test
    void batchUpdate_categoryNone_leavesCategoryUntouched() {
        Photo p1 = new Photo(); p1.setId(1L); p1.setName("a");
        Category c = new Category("cat"); c.setId(5L);
        p1.setCategory(c);
        when(photoRepo.findAllById(List.of(1L))).thenReturn(List.of(p1));

        service.batchUpdate(batchReq(List.of(1L)));
        assertThat(p1.getCategory()).isSameAs(c);
    }

    @Test
    void batchUpdate_categorySet_missingCategory_shouldThrow404() {
        Photo p1 = new Photo(); p1.setId(1L); p1.setName("a");
        when(photoRepo.findAllById(List.of(1L))).thenReturn(List.of(p1));
        when(catRepo.findById(99L)).thenReturn(Optional.empty());

        BatchPhotoUpdateRequest req = batchReq(List.of(1L));
        req.setCategoryOp(BatchPhotoUpdateRequest.CategoryOp.SET);
        req.setCategoryId(99L);

        assertThatThrownBy(() -> service.batchUpdate(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类不存在");
    }

    @Test
    void batchUpdate_albumAdd_delegatesToAlbumService() {
        Photo p1 = new Photo(); p1.setId(1L); p1.setName("a");
        when(photoRepo.findAllById(List.of(1L))).thenReturn(List.of(p1));

        BatchPhotoUpdateRequest req = batchReq(List.of(1L));
        req.setAddAlbumIds(List.of(3L));

        service.batchUpdate(req);
        verify(albumService).addPhotos(3L, List.of(1L));
    }

    @Test
    void batchUpdate_albumRemove_delegatesToAlbumService() {
        Photo p1 = new Photo(); p1.setId(1L); p1.setName("a");
        when(photoRepo.findAllById(List.of(1L))).thenReturn(List.of(p1));

        BatchPhotoUpdateRequest req = batchReq(List.of(1L));
        req.setRemoveAlbumIds(List.of(3L));

        service.batchUpdate(req);
        verify(albumService).removePhotos(3L, List.of(1L));
    }

    @Test
    void batchUpdate_skipMissingPhotos() {
        Photo p1 = new Photo(); p1.setId(1L); p1.setName("a");
        when(photoRepo.findAllById(List.of(1L, 2L))).thenReturn(List.of(p1));

        List<PhotoResponse> result = service.batchUpdate(batchReq(List.of(1L, 2L)));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void batchUpdate_emptyResult_returnsEmptyList() {
        when(photoRepo.findAllById(List.of(99L))).thenReturn(List.of());

        List<PhotoResponse> result = service.batchUpdate(batchReq(List.of(99L)));
        assertThat(result).isEmpty();
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
        long verified = new com.hape.photogallery.config.MediaSignatureService(
                "test-secret-0123456789abcdef0123456789abcdef", 300)
                .verify(items.get(0).getMediaToken());
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
