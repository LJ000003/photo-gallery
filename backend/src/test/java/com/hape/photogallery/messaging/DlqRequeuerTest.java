package com.hape.photogallery.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;

import com.hape.photogallery.config.RabbitMQConfig;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.ProcessingStatus;
import com.hape.photogallery.repository.PhotoRepository;

/**
 * DLQ 死信恢复测试（直接 new，绕开 @ConditionalOnProperty 的上下文加载）：
 * FAILED 照片置回 PROCESSING 并重新入队；DONE/不存在/非 FAILED 丢弃；
 * 发布失败时状态保持 PROCESSING（5 分钟重扫兜底）。
 */
@ExtendWith(MockitoExtension.class)
class DlqRequeuerTest {

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private PhotoRepository photoRepo;

    private DlqRequeuer requeuer;

    @BeforeEach
    void setUp() {
        requeuer = new DlqRequeuer(rabbitTemplate, photoRepo);
    }

    private ProcessingMessage msg(long photoId) {
        return new ProcessingMessage(photoId, "2026/07", "a.jpg", "水印");
    }

    private Photo failedPhoto(long id) {
        Photo photo = new Photo();
        photo.setId(id);
        photo.setProcessingStatus(ProcessingStatus.FAILED);
        photo.setErrorMessage("处理失败（已重试3次）: boom");
        return photo;
    }

    @Test
    void drain_failedPhoto_shouldRequeueWithStatusProcessing() {
        when(rabbitTemplate.receiveAndConvert(RabbitMQConfig.DLQ))
                .thenReturn(msg(1L))
                .thenReturn(null); // 第二次取 → 空队列结束
        Photo photo = failedPhoto(1L);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));

        requeuer.drain();

        // 先落 PROCESSING + 清错误信息
        verify(photoRepo).save(photo);
        org.assertj.core.api.Assertions.assertThat(photo.getProcessingStatus())
                .isEqualTo(ProcessingStatus.PROCESSING);
        org.assertj.core.api.Assertions.assertThat(photo.getErrorMessage()).isNull();
        // 重新发布主队列（新消息，无 x-retry-count → consumer 从 0 重新重试）
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.ROUTING_KEY),
                        any(ProcessingMessage.class));
    }

    @Test
    void drain_donePhoto_shouldDropWithoutRequeue() {
        when(rabbitTemplate.receiveAndConvert(RabbitMQConfig.DLQ))
                .thenReturn(msg(1L))
                .thenReturn(null);
        Photo photo = new Photo();
        photo.setId(1L);
        photo.setProcessingStatus(ProcessingStatus.DONE);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));

        requeuer.drain();

        verify(photoRepo, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void drain_missingPhoto_shouldDrop() {
        when(rabbitTemplate.receiveAndConvert(RabbitMQConfig.DLQ))
                .thenReturn(msg(99L))
                .thenReturn(null);
        when(photoRepo.findById(99L)).thenReturn(Optional.empty());

        requeuer.drain();

        verify(photoRepo, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void drain_emptyQueue_shouldDoNothing() {
        when(rabbitTemplate.receiveAndConvert(RabbitMQConfig.DLQ)).thenReturn(null);

        requeuer.drain();

        verify(photoRepo, never()).findById(any());
    }

    @Test
    void drain_publishFailure_shouldKeepStatusProcessingForRescan() {
        when(rabbitTemplate.receiveAndConvert(RabbitMQConfig.DLQ))
                .thenReturn(msg(1L))
                .thenReturn(null);
        Photo photo = failedPhoto(1L);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));
        // convertAndSend 是 void —— 必须用 doThrow().when() 形式（when(void) 不合法）
        doThrow(new RuntimeException("queue full"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(ProcessingMessage.class));

        requeuer.drain();

        // 发布失败但状态已落 PROCESSING——PhotoService.recoverStuckProcessing（5 分钟重扫）兜底
        org.assertj.core.api.Assertions.assertThat(photo.getProcessingStatus())
                .isEqualTo(ProcessingStatus.PROCESSING);
        verify(photoRepo).save(photo);
    }

    @Test
    void drain_receiveFailure_shouldAbortGracefully() {
        when(rabbitTemplate.receiveAndConvert(RabbitMQConfig.DLQ))
                .thenThrow(new RuntimeException("connection refused"));

        requeuer.drain(); // 不抛出，下轮再试

        verify(photoRepo, never()).findById(any());
    }

    @Test
    void drain_conversionFailure_shouldDropAndContinue() {
        // 第一条消息反序列化失败（升级遗留旧格式/损坏）→ 丢弃不中断；第二条正常 → 仍被恢复
        when(rabbitTemplate.receiveAndConvert(RabbitMQConfig.DLQ))
                .thenThrow(new MessageConversionException("cannot parse"))
                .thenReturn(msg(1L))
                .thenReturn(null);
        Photo photo = failedPhoto(1L);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));

        requeuer.drain();

        // 坏消息后的正常消息仍被 drain（修复前 catch(Exception) 会 return 中断整轮）
        verify(photoRepo).save(photo);
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.ROUTING_KEY),
                        any(ProcessingMessage.class));
    }

    @Test
    void drain_onlyConversionFailures_shouldNotTouchRepo() {
        // 注意：thenThrow 链的最后一个 stub 会无限重复（Mockito 语义，不会回退默认值）——
        // 必须以 thenReturn(null) 收尾模拟空队列，否则 drain 死循环
        when(rabbitTemplate.receiveAndConvert(RabbitMQConfig.DLQ))
                .thenThrow(new MessageConversionException("bad json"))
                .thenThrow(new MessageConversionException("bad json"))
                .thenReturn(null);

        requeuer.drain(); // 不抛出

        verify(photoRepo, never()).findById(any());
        verify(photoRepo, never()).save(any());
    }
}
