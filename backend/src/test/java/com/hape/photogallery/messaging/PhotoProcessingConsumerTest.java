package com.hape.photogallery.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.repository.PhotoRepository;
import com.hape.photogallery.service.PhotoProcessor;
import com.hape.photogallery.service.StorageService;
import com.rabbitmq.client.Channel;

/**
 * 消息消费者 ack/nack/DLQ 语义测试（直接 new，绕开 @ConditionalOnProperty 的上下文加载）。
 * 用例：照片不存在直接 ack / 成功 ack / 失败 requeue 重试（header 递增）/ 达上限转 DLQ + 标 FAILED。
 */
@ExtendWith(MockitoExtension.class)
class PhotoProcessingConsumerTest {

    @Mock private PhotoProcessor processor;
    @Mock private PhotoRepository photoRepo;
    @Mock private StorageService storage;
    @Mock private Channel channel;

    @TempDir Path tempDir;

    private PhotoProcessingConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PhotoProcessingConsumer(processor, photoRepo, storage);
    }

    private Message makeMessage(long deliveryTag, Integer retryCount) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(deliveryTag);
        if (retryCount != null) props.setHeader("x-retry-count", retryCount);
        return new Message(new byte[0], props);
    }

    @Test
    void handle_photoNotFound_shouldAckAndDrop() throws Exception {
        when(photoRepo.findById(1L)).thenReturn(Optional.empty());

        consumer.handle(new ProcessingMessage(1L, "2024/08", "a.jpg", null),
                makeMessage(42L, null), channel);

        verify(channel).basicAck(42L, false);
        verify(processor, never()).process(anyLong(), any(), any(), any(), any());
    }

    @Test
    void handle_success_shouldAck() throws Exception {
        Photo photo = new Photo();
        photo.setId(1L);
        photo.setFileName("2024/08/a.jpg");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));
        when(storage.getUploadDir()).thenReturn(tempDir);

        consumer.handle(new ProcessingMessage(1L, "2024/08", "a.jpg", "wm"),
                makeMessage(42L, null), channel);

        verify(processor).process(eq(1L), any(), eq("2024/08"), eq("a.jpg"), eq("wm"));
        verify(channel).basicAck(42L, false);
    }

    @Test
    void handle_failure_belowMaxRetries_shouldRequeueWithIncrementingHeader() throws Exception {
        Photo photo = new Photo();
        photo.setId(1L);
        photo.setFileName("x.jpg");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));
        when(storage.getUploadDir()).thenReturn(tempDir);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(processor).process(anyLong(), any(), any(), any(), any());

        consumer.handle(new ProcessingMessage(1L, "2024/08", "x.jpg", null),
                makeMessage(42L, 2), channel);

        // 第 3 次尝试仍 < MAX_RETRIES(3) → requeue 并递增 header
        verify(channel).basicNack(42L, false, true);
        verify(photoRepo, never()).save(any());
    }

    @Test
    void handle_failure_atMaxRetries_shouldNackToDlqAndMarkFailed() throws Exception {
        Photo photo = new Photo();
        photo.setId(1L);
        photo.setFileName("x.jpg");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));
        when(storage.getUploadDir()).thenReturn(tempDir);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(processor).process(anyLong(), any(), any(), any(), any());

        consumer.handle(new ProcessingMessage(1L, "2024/08", "x.jpg", null),
                makeMessage(42L, 3), channel);

        // 已达 MAX_RETRIES → 不 requeue（进 DLQ）+ 标记 FAILED
        verify(channel).basicNack(42L, false, false);
        ArgumentCaptor<Photo> captor = ArgumentCaptor.forClass(Photo.class);
        verify(photoRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getProcessingStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getErrorMessage()).contains("处理失败");
    }
}
