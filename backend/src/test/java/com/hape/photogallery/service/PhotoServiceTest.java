package com.hape.photogallery.service;

import com.hape.photogallery.config.MediaSignatureService;
import com.hape.photogallery.dto.BatchPhotoUpdateRequest;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.PhotoUpdateRequest;
import com.hape.photogallery.dto.UploadParams;
import com.hape.photogallery.entity.Category;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 照片写路径服务测试（从原 981 行拆出后保留的写路径用例）：
 * 上传/批量上传/更新/删除/批量删除/批量编辑。查询与回收站用例已迁至
 * PhotoQueryServiceTest / TrashServiceTest，本类用例原样保留不删改断言。
 */
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
        // mock TransactionStatus 防止回调内 setRollbackOnly()/isRollbackOnly() 对 null NPE
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });

        // 查询侧走真实 PhotoQueryService：getById/toResponse 经跨 bean 代理语义；
        // FullTextProbe(dataSource=null) → 按「支持 FULLTEXT」处理（与拆出前默认 service 一致）
        MediaSignatureService mediaSignature = new MediaSignatureService(
                "test-secret-0123456789abcdef0123456789abcdef", 300);
        PhotoQueryService queryService = new PhotoQueryService(photoRepo, exifRepo, exifService,
                mediaSignature, storage, new FullTextProbe(null));
        service = new PhotoService(photoRepo, tagRepo, catRepo,
                imageService, albumService, storage, processingSender, transactionTemplate,
                new FilePathResolver(photoRepo, storage), queryService);
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
        // 并发同 hash 上传：check-then-insert 竞态撞唯一索引 → 删残留文件 + 重查返回 DuplicateException
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

    @Test
    void delete_shouldReselectAlbumCovers() {
        // P1 修复：软删后以其为封面的相册必须重选，否则封面悬空（viewer 404 / 彻底删除后永久失效）
        Photo p = new Photo(); p.setId(1L); p.setName("test");
        p.setFileName("2026/07/test.jpg");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));

        service.delete(1L);

        verify(albumService).reselectCoversAfterPhotoDeletion(1L);
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

    // ==================== retryProcessing / 卡死恢复 ====================

    @Test
    void retryProcessing_shouldResetStatusAndResend() throws IOException {
        Path filePath = tempDir.resolve("2026/07/test.jpg");
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, JPEG_BYTES);

        Photo p = new Photo();
        p.setId(1L);
        p.setName("test");
        p.setFileName("2026/07/test.jpg");
        p.setProcessingStatus(ProcessingStatus.FAILED);
        p.setErrorMessage("上次失败");
        p.setWatermark("wm"); // V11：水印落库，重试补发从 DB 恢复
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));

        service.retryProcessing(1L);

        // 状态重置 + 重发处理消息（无事务直调 → 立即发送，与 upload 的 SendAfterCommit 同语义）
        assertThat(p.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSING);
        assertThat(p.getErrorMessage()).isNull();
        verify(photoRepo).save(p);
        verify(processingSender).send(eq(1L), eq(tempDir.resolve("2026/07/test.jpg")),
                eq("2026/07"), eq("test.jpg"), eq("wm"));
    }

    @Test
    void retryProcessing_notFound_shouldThrow404() {
        when(photoRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retryProcessing(99L))
                .isInstanceOf(BusinessException.class);
        verify(processingSender, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void recoverStuckProcessing_shouldResendAllProcessingPhotos() throws IOException {
        Path filePath = tempDir.resolve("2026/07/test.jpg");
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, JPEG_BYTES);

        Photo p1 = new Photo();
        p1.setId(1L);
        p1.setFileName("2026/07/test.jpg");
        p1.setProcessingStatus(ProcessingStatus.PROCESSING);
        p1.setWatermark("wm1");
        Photo p2 = new Photo();
        p2.setId(2L);
        p2.setFileName("2026/07/test2.jpg");
        p2.setProcessingStatus(ProcessingStatus.PROCESSING);
        // 无水印照片 → 透传 null（语义：重扫不凭空造水印）
        when(photoRepo.findByProcessingStatus(ProcessingStatus.PROCESSING)).thenReturn(List.of(p1, p2));

        service.recoverStuckProcessing();

        // 水印从 DB 恢复（V11）：消息丢失后重扫补发不再丢水印
        verify(processingSender).send(eq(1L), any(Path.class), eq("2026/07"), eq("test.jpg"), eq("wm1"));
        verify(processingSender).send(eq(2L), any(Path.class), eq("2026/07"), eq("test2.jpg"), isNull());
    }

    @Test
    void recoverStuckProcessing_sendFailure_shouldKeepProcessing() throws IOException {
        Path filePath = tempDir.resolve("2026/07/test.jpg");
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, JPEG_BYTES);

        Photo p = new Photo();
        p.setId(1L);
        p.setFileName("2026/07/test.jpg");
        p.setProcessingStatus(ProcessingStatus.PROCESSING);
        when(photoRepo.findByProcessingStatus(ProcessingStatus.PROCESSING)).thenReturn(List.of(p));
        doThrow(new RuntimeException("rabbit down")).when(processingSender)
                .send(any(Long.class), any(Path.class), any(), any(), any());

        service.recoverStuckProcessing();

        // 发送失败（Rabbit broker 抖动）不判死：保持 PROCESSING 等下次 5 分钟重扫
        assertThat(p.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSING);
        assertThat(p.getErrorMessage()).isNull();
        verify(photoRepo, never()).save(p);
    }

    @Test
    void recoverStuckProcessing_noStuck_shouldNoOp() {
        when(photoRepo.findByProcessingStatus(ProcessingStatus.PROCESSING)).thenReturn(List.of());

        service.recoverStuckProcessing();

        verify(processingSender, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void recoverStuckOnStartup_shouldTriggerRecovery() throws IOException {
        Path filePath = tempDir.resolve("2026/07/test.jpg");
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, JPEG_BYTES);

        Photo p = new Photo();
        p.setId(1L);
        p.setFileName("2026/07/test.jpg");
        p.setProcessingStatus(ProcessingStatus.PROCESSING);
        p.setWatermark("wm-startup");
        when(photoRepo.findByProcessingStatus(ProcessingStatus.PROCESSING)).thenReturn(List.of(p));

        service.recoverStuckOnStartup();

        verify(processingSender).send(eq(1L), any(Path.class), eq("2026/07"), eq("test.jpg"),
                eq("wm-startup"));
    }
}
