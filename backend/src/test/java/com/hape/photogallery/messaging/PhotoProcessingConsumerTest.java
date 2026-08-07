package com.hape.photogallery.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.hape.photogallery.config.RabbitMQConfig;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.ProcessingStatus;
import com.hape.photogallery.repository.PhotoRepository;
import com.hape.photogallery.service.PhotoProcessor;
import com.hape.photogallery.service.StorageService;
import com.rabbitmq.client.Channel;

/**
 * 消息消费者 ack / TTL 重试队列 / DLQ 语义测试（直接 new，绕开 @ConditionalOnProperty 的上下文加载）。
 *  后语义：失败时显式重投到 TTL 重试队列（rabbitTemplate.send 原样转发，header 递增 x-retry-count）+ ack；
 * 达上限标 FAILED + nack 进 DLQ。计数用自定义 header（随显式重投持久化，与 broker 版本无关）。
 */
@ExtendWith(MockitoExtension.class)
class PhotoProcessingConsumerTest {

    @Mock private PhotoProcessor processor;
    @Mock private PhotoRepository photoRepo;
    @Mock private StorageService storage;
    @Mock private Channel channel;
    @Mock private RabbitTemplate rabbitTemplate;

    @TempDir Path tempDir;

    private PhotoProcessingConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PhotoProcessingConsumer(processor, photoRepo, storage, rabbitTemplate);
    }

    /** 构造带 x-retry-count header 的消息；retryCount 为 null 表示初投（无 header） */
    private Message makeMessage(long deliveryTag, Integer retryCount) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(deliveryTag);
        if (retryCount != null) props.setHeader("x-retry-count", retryCount);
        return new Message(new byte[0], props);
    }

    private Photo photo() {
        Photo photo = new Photo();
        photo.setId(1L);
        photo.setFileName("x.jpg");
        return photo;
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
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo()));
        when(storage.getUploadDir()).thenReturn(tempDir);

        consumer.handle(new ProcessingMessage(1L, "2024/08", "a.jpg", "wm"),
                makeMessage(42L, null), channel);

        verify(processor).process(eq(1L), any(), eq("2024/08"), eq("a.jpg"), eq("wm"));
        verify(channel).basicAck(42L, false);
    }

    @Test
    void handle_failure_belowMaxRetries_shouldPublishToRetryQueueWithIncrementedHeaderAndAck() throws Exception {
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo()));
        when(storage.getUploadDir()).thenReturn(tempDir);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(processor).process(anyLong(), any(), any(), any(), any());
        Message amqpMsg = makeMessage(42L, 2); // 第 3 次尝试仍 < MAX_RETRIES(3)

        consumer.handle(new ProcessingMessage(1L, "2024/08", "x.jpg", null), amqpMsg, channel);

        // 重投 TTL 重试队列（同一 Message 实例，header 递增为 3）+ ack，不再 requeue
        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.RETRY_ROUTING_KEY), sent.capture());
        assertThat(sent.getValue()).isSameAs(amqpMsg);
        assertThat(sent.getValue().getMessageProperties().getHeaders().get("x-retry-count"))
                .isEqualTo(3);
        verify(channel).basicAck(42L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verify(photoRepo, never()).save(any());
    }

    @Test
    void handle_failure_firstAttempt_shouldRetry() throws Exception {
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo()));
        when(storage.getUploadDir()).thenReturn(tempDir);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(processor).process(anyLong(), any(), any(), any(), any());

        consumer.handle(new ProcessingMessage(1L, "2024/08", "x.jpg", null),
                makeMessage(42L, null), channel); // 初投无 header → count=0 → 走重试

        verify(rabbitTemplate).send(eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.RETRY_ROUTING_KEY), any(Message.class));
        verify(channel).basicAck(42L, false);
    }

    @Test
    void handle_failure_atMaxRetries_shouldNackToDlqAndMarkFailed() throws Exception {
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo()));
        when(storage.getUploadDir()).thenReturn(tempDir);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(processor).process(anyLong(), any(), any(), any(), any());

        consumer.handle(new ProcessingMessage(1L, "2024/08", "x.jpg", null),
                makeMessage(42L, 3), channel); // 已达 MAX_RETRIES

        // 不再重投 → nack(requeue=false) 走主队列 DLX 进 DLQ + 标记 FAILED
        verify(rabbitTemplate, never()).send(any(), any(), any(Message.class));
        verify(channel).basicNack(42L, false, false);
        ArgumentCaptor<Photo> captor = ArgumentCaptor.forClass(Photo.class);
        verify(photoRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(captor.getValue().getErrorMessage()).contains("处理失败");
    }
}
