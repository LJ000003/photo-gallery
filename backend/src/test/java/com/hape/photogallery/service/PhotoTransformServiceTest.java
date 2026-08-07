package com.hape.photogallery.service;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** 照片变换（拆分自 PhotoService； 事务边界 + 补偿） */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class PhotoTransformServiceTest {

    @Mock private PhotoRepository photoRepo;
    @Mock private ImageProcessingService imageService;
    @Mock private StorageService storage;
    @Mock private ExifService exifService;
    @Mock private TransactionTemplate transactionTemplate;

    @TempDir Path tempDir;

    private PhotoTransformService service;

    @BeforeEach
    void setUp() {
        when(storage.getUploadDir()).thenReturn(tempDir);
        when(storage.resolveSafe(any())).thenAnswer(inv ->
                tempDir.resolve((String) inv.getArgument(0)).normalize());
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });
        service = new PhotoTransformService(photoRepo, imageService, storage, exifService,
                transactionTemplate, new FilePathResolver(photoRepo, storage));
    }

    @Test
    void transform_saveFailure_shouldRestoreOriginalFileAndCleanupBackup() throws IOException {
        // 变换成功写回 → 事务内 save 失败 → 补偿恢复原图（磁盘与 DB 一致），备份文件清理
        Path filePath = tempDir.resolve("2026/07/t.jpg");
        Files.createDirectories(filePath.getParent());
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(8, 8,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillRect(0, 0, 8, 8);
        g.dispose();
        javax.imageio.ImageIO.write(img, "jpeg", filePath.toFile());
        byte[] original = Files.readAllBytes(filePath);

        Photo p = new Photo(); p.setId(1L); p.setName("t");
        p.setFileName("2026/07/t.jpg");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(imageService.decodeCapped(any())).thenReturn(img);
        when(imageService.rotateImage(any(), anyInt())).thenAnswer(inv -> inv.getArgument(0));
        when(imageService.getFormat(any())).thenReturn("JPEG");
        when(photoRepo.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.transformPhoto(1L, 90, "none", null, null, null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db down");

        // 补偿生效：原图字节与变换前一致
        assertThat(Files.readAllBytes(filePath)).isEqualTo(original);
        // 备份文件被清理
        assertThat(Files.exists(filePath.resolveSibling("t.jpg.bak"))).isFalse();
    }

    @Test
    void transform_corruptImage_shouldThrow400() throws IOException {
        Path filePath = tempDir.resolve("2026/07/bad.jpg");
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, "not an image at all".getBytes());

        Photo p = new Photo(); p.setId(1L); p.setName("bad");
        p.setFileName("2026/07/bad.jpg");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.transformPhoto(1L, 90, "none", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法处理");
        verify(photoRepo, never()).save(any());
    }

    @Test
    void transform_notFound_shouldThrow404() {
        when(photoRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.transformPhoto(99L, 90, "none", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void transform_success_shouldPersistFileSize() throws IOException {
        Path filePath = tempDir.resolve("2026/07/ok.jpg");
        Files.createDirectories(filePath.getParent());
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(4, 4,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(img, "jpeg", filePath.toFile());

        Photo p = new Photo(); p.setId(1L); p.setName("ok");
        p.setFileName("2026/07/ok.jpg");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(imageService.decodeCapped(any())).thenReturn(img);
        when(imageService.getFormat(any())).thenReturn("JPEG");

        service.transformPhoto(1L, 0, "none", null, null, null, null);

        // 事务内 save 被调用且 fileSize 已更新为变换后大小
        verify(photoRepo).save(argThat(saved -> saved.getFileSize() != null));
    }

    @Test
    void transform_success_shouldResetOriginalProcessed() throws IOException {
        // 变换重写原图（可能裁掉/翻转水印）→ original_processed 必须复位，
        // 否则下次 retry 会跳过水印导致「水印被裁掉后永不再补」（V12 幂等标记联动）
        Path filePath = tempDir.resolve("2026/07/wm.jpg");
        Files.createDirectories(filePath.getParent());
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(4, 4,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(img, "jpeg", filePath.toFile());

        Photo p = new Photo(); p.setId(1L); p.setName("wm");
        p.setFileName("2026/07/wm.jpg");
        p.setOriginalProcessed(true);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(imageService.decodeCapped(any())).thenReturn(img);
        when(imageService.getFormat(any())).thenReturn("JPEG");

        service.transformPhoto(1L, 0, "none", null, null, null, null);

        verify(photoRepo).save(argThat(saved -> !saved.isOriginalProcessed()));
    }
}
