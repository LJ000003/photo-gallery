package com.hape.photogallery.service;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.PhotoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回收站服务测试（从 PhotoServiceTest 拆出）：
 * 恢复/永久删除/30 天定时清理。
 * <p>
 * purge 原子性（P0 修复）语义：先 native 条件删除（hardDeleteIfStillDeleted）后删文件，
 * 每张照片独立 REQUIRES_NEW 事务；期间被恢复的照片条件删除返回 0 → 跳过文件删除。
 * ResourcelessTransactionManager 是真实无资源事务管理器（支持 REQUIRES_NEW），
 * 保证 TransactionTemplate 的 execute 回调真实执行。
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TrashServiceTest {

    @Mock private PhotoRepository photoRepo;
    @Mock private StorageService storage;
    @Mock private AlbumService albumService;
    @Mock private PhotoQueryService photoQueryService;

    private TrashService service;

    @BeforeEach
    void setUp() {
        // 真实 FilePathResolver：删除文件走 storage.deleteFile（原图 + 缩略图×2 + webp）
        // TransactionTemplate 用 mock PlatformTransactionManager + SimpleTransactionStatus 驱动执行
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new TrashService(photoRepo, new FilePathResolver(photoRepo, storage),
                albumService, photoQueryService, txManager);
    }

    @Test
    void restore_notDeleted_should404() {
        when(photoRepo.findDeletedById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到可恢复的照片");
        verify(photoRepo, never()).save(any());
    }

    @Test
    void restore_success_shouldClearDeletedAt() {
        Photo p = new Photo();
        p.setId(1L);
        p.setName("已删");
        p.setDeletedAt(LocalDateTime.now());
        when(photoRepo.findDeletedById(1L)).thenReturn(Optional.of(p));

        service.restore(1L);

        assertThat(p.getDeletedAt()).isNull();
        verify(photoRepo).save(p);
    }

    @Test
    void restore_shouldBackfillFileHashWhenFree() throws Exception {
        // 原文件真实存在于临时目录：恢复后 fileHash 回填（delete 清空它，不回填则去重永久失效）
        java.nio.file.Path original = java.nio.file.Files.createTempFile("orig", ".jpg");
        java.nio.file.Files.write(original, new byte[] {1, 2, 3, 4, 5});
        Photo p = new Photo();
        p.setId(1L);
        p.setFileName("2026/07/a.jpg");
        p.setDeletedAt(LocalDateTime.now());
        when(photoRepo.findDeletedById(1L)).thenReturn(Optional.of(p));
        when(storage.resolveSafe("2026/07/a.jpg")).thenReturn(original);
        when(photoRepo.findWithDetailsByFileHash(any())).thenReturn(Optional.empty());

        service.restore(1L);

        assertThat(p.getDeletedAt()).isNull();
        String expected = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(new byte[] {1, 2, 3, 4, 5}));
        assertThat(p.getFileHash()).isEqualTo(expected);
    }

    @Test
    void restore_hashOccupied_shouldLeaveHashNull() throws Exception {
        java.nio.file.Path original = java.nio.file.Files.createTempFile("orig", ".jpg");
        java.nio.file.Files.write(original, new byte[] {9, 9, 9});
        Photo p = new Photo();
        p.setId(1L);
        p.setFileName("2026/07/a.jpg");
        p.setDeletedAt(LocalDateTime.now());
        when(photoRepo.findDeletedById(1L)).thenReturn(Optional.of(p));
        when(storage.resolveSafe("2026/07/a.jpg")).thenReturn(original);
        // 软删期间同文件已作为新照片入库（hash 唯一索引）→ 回填会破坏去重，必须留空
        Photo other = new Photo();
        other.setId(99L);
        when(photoRepo.findWithDetailsByFileHash(any())).thenReturn(Optional.of(other));

        service.restore(1L);

        assertThat(p.getFileHash()).isNull();
    }

    @Test
    void restore_fileMissing_shouldLeaveHashNullWithoutThrowing() {
        Photo p = new Photo();
        p.setId(1L);
        p.setFileName("2026/07/missing.jpg");
        p.setDeletedAt(LocalDateTime.now());
        when(photoRepo.findDeletedById(1L)).thenReturn(Optional.of(p));
        // resolveSafe 返回不存在的路径 → 哈希留空，恢复流程不中断
        when(storage.resolveSafe("2026/07/missing.jpg"))
                .thenReturn(java.nio.file.Path.of("not-exists"));
        when(photoRepo.findWithDetailsByFileHash(any())).thenReturn(Optional.empty());

        service.restore(1L);

        assertThat(p.getDeletedAt()).isNull();
        assertThat(p.getFileHash()).isNull();
    }

    @Test
    void permanentlyDelete_shouldConditionalDeleteThenFiles() {
        Photo p = new Photo();
        p.setId(1L);
        p.setName("已删");
        p.setFileName("2026/07/test.jpg");
        when(photoRepo.findDeletedById(1L)).thenReturn(Optional.of(p));
        when(photoRepo.hardDeleteIfStillDeleted(1L)).thenReturn(1);

        service.permanentlyDelete(1L);

        // 先条件删行、后删文件（崩溃只留孤儿文件，不产生幽灵记录）
        verify(photoRepo).hardDeleteIfStillDeleted(1L);
        verify(photoRepo, never()).delete(any(Photo.class));
        // 真实 FilePathResolver：原图 + 缩略图(含 200) + webp 四文件清理
        verify(storage, times(4)).deleteFile(any());
        // 封面重选接入点（硬删路径）——与软删路径同标准验证，防回归
        verify(albumService).reselectCoversAfterPhotoDeletion(1L);
    }

    @Test
    void permanentlyDelete_restoredRace_shouldAbortWithoutFileDeletion() {
        Photo p = new Photo();
        p.setId(1L);
        p.setName("已删");
        p.setFileName("2026/07/test.jpg");
        when(photoRepo.findDeletedById(1L)).thenReturn(Optional.of(p));
        // 期间被恢复 → 条件删除 0 行 → 中止，绝不删已恢复照片的磁盘文件
        when(photoRepo.hardDeleteIfStillDeleted(1L)).thenReturn(0);

        assertThatThrownBy(() -> service.permanentlyDelete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已恢复");
        verify(storage, never()).deleteFile(any());
        verify(albumService, never()).reselectCoversAfterPhotoDeletion(any());
    }

    @Test
    void permanentlyDelete_notFound_should404() {
        when(photoRepo.findDeletedById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.permanentlyDelete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到该照片");
        verify(photoRepo, never()).hardDeleteIfStillDeleted(any());
    }

    @Test
    void cleanupDeletedPermanently_shouldPurgeExpired() {
        Photo p1 = new Photo();
        p1.setId(1L);
        p1.setFileName("2026/07/a.jpg");
        Photo p2 = new Photo();
        p2.setId(2L);
        p2.setFileName("2026/07/b.jpg");
        when(photoRepo.findDeletedBefore(any())).thenReturn(List.of(p1, p2));
        when(photoRepo.hardDeleteIfStillDeleted(1L)).thenReturn(1);
        when(photoRepo.hardDeleteIfStillDeleted(2L)).thenReturn(1);

        service.cleanupDeletedPermanently();

        verify(photoRepo).hardDeleteIfStillDeleted(1L);
        verify(photoRepo).hardDeleteIfStillDeleted(2L);
        verify(storage, atLeast(8)).deleteFile(any());
        // 30 天定时清理的封面重选接入点——防回归
        verify(albumService).reselectCoversAfterPhotoDeletion(1L);
        verify(albumService).reselectCoversAfterPhotoDeletion(2L);
    }

    @Test
    void cleanupDeletedPermanently_restoredRace_shouldSkipFileDeletion() {
        Photo p1 = new Photo();
        p1.setId(1L);
        p1.setFileName("2026/07/a.jpg");
        Photo p2 = new Photo();
        p2.setId(2L);
        p2.setFileName("2026/07/b.jpg");
        when(photoRepo.findDeletedBefore(any())).thenReturn(List.of(p1, p2));
        when(photoRepo.hardDeleteIfStillDeleted(1L)).thenReturn(0); // 期间被恢复
        when(photoRepo.hardDeleteIfStillDeleted(2L)).thenReturn(1);

        service.cleanupDeletedPermanently();

        // 恢复的照片：文件保留、封面不重选（正是要防的「恢复竞态连坐硬删」）
        verify(storage, times(4)).deleteFile(any()); // 仅 p2 的四个文件
        verify(albumService, never()).reselectCoversAfterPhotoDeletion(1L);
        verify(albumService).reselectCoversAfterPhotoDeletion(2L);
    }

    @Test
    void cleanupDeletedPermanently_emptyList_shouldNoOp() {
        when(photoRepo.findDeletedBefore(any())).thenReturn(List.of());

        service.cleanupDeletedPermanently();

        verify(photoRepo, never()).hardDeleteIfStillDeleted(any());
        verify(storage, never()).deleteFile(any());
    }
}
