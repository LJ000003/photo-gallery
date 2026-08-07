package com.hape.photogallery.service;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.ProcessingStatus;
import com.hape.photogallery.repository.PhotoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dev 模式（@Async）处理失败终态落库（P2 修复）：
 * PhotoProcessor 失败只抛错不落状态，本类负责落 FAILED + errorMessage。
 */
@ExtendWith(MockitoExtension.class)
class AsyncImageProcessorTest {

    @Mock private PhotoProcessor processor;
    @Mock private PhotoRepository photoRepo;

    private AsyncImageProcessor sender;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        sender = new AsyncImageProcessor(processor, photoRepo);
    }

    @Test
    void send_success_shouldNotTouchStatus() {
        Path target = tempDir.resolve("a.jpg");

        sender.send(1L, target, "2026/08", "a.jpg", null);

        verify(photoRepo, never()).save(any());
    }

    @Test
    void send_failure_shouldMarkFailedWithMessage() {
        Photo photo = new Photo();
        photo.setId(1L);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));
        doThrow(new RuntimeException("boom")).when(processor)
                .process(1L, tempDir.resolve("a.jpg"), "2026/08", "a.jpg", null);

        sender.send(1L, tempDir.resolve("a.jpg"), "2026/08", "a.jpg", null);

        assertThat(photo.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(photo.getErrorMessage()).contains("boom");
        verify(photoRepo).save(photo);
    }

    @Test
    void send_failure_photoMissing_shouldNotThrow() {
        when(photoRepo.findById(1L)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("boom")).when(processor)
                .process(1L, tempDir.resolve("a.jpg"), "2026/08", "a.jpg", null);

        sender.send(1L, tempDir.resolve("a.jpg"), "2026/08", "a.jpg", null);
        // 不抛异常（@Async 线程内吞掉，日志可见）
    }
}
