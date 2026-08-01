package com.hape.photogallery.messaging;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.hape.photogallery.config.RabbitMQConfig;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.repository.PhotoRepository;
import com.hape.photogallery.service.PhotoProcessor;
import com.rabbitmq.client.Channel;

@Component
@ConditionalOnProperty(name = "photo.processing.type", havingValue = "rabbitmq")
public class PhotoProcessingConsumer {

    private static final Logger log = LoggerFactory.getLogger(PhotoProcessingConsumer.class);
    private static final int MAX_RETRIES = 3;

    private final PhotoProcessor processor;
    private final PhotoRepository photoRepo;

    public PhotoProcessingConsumer(PhotoProcessor processor, PhotoRepository photoRepo) {
        this.processor = processor;
        this.photoRepo = photoRepo;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void handle(ProcessingMessage msg, Message amqpMsg, Channel channel) throws IOException {
        Long photoId = msg.getPhotoId();
        long deliveryTag = amqpMsg.getMessageProperties().getDeliveryTag();

        // 传播 X-Trace-Id 到 MDC
        String traceId = (String) amqpMsg.getMessageProperties().getHeaders().get("X-Trace-Id");
        if (traceId != null) MDC.put("traceId", traceId);

        try {
            // 照片已删除，直接 ack 丢弃消息
            Photo photo = photoRepo.findById(photoId).orElse(null);
            if (photo == null) {
                log.warn("照片不存在，丢弃消息 photo={}", photoId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 解析文件路径
            Path target = Path.of(msg.getTargetPath());
            processor.process(photoId, target, msg.getDateDir(), msg.getBaseName(), msg.getWatermark());

            // 成功 → ack
            channel.basicAck(deliveryTag, false);
        } catch (Throwable e) {
            int retryCount = getRetryCount(amqpMsg);
            if (retryCount < MAX_RETRIES) {
                log.warn("处理失败（第 {} 次重试）photo={}: {}", retryCount + 1, photoId, e.getMessage());
                // requeue：消息回到队尾等待重试
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("处理失败（已达最大重试 {} 次）photo={}，转入 DLQ", MAX_RETRIES, photoId, e);
                // 标记 FAILED + 不 requeue → 消息进入 DLQ
                try {
                    Photo photo = photoRepo.findById(photoId).orElse(null);
                    if (photo != null) {
                        String errMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                        if (errMsg.length() > 500) errMsg = errMsg.substring(0, 497) + "...";
                        photo.setProcessingStatus("FAILED");
                        photo.setErrorMessage("处理失败（已重试" + MAX_RETRIES + "次）: " + errMsg);
                        photoRepo.save(photo);
                    }
                } catch (Throwable inner) {
                    log.error("无法保存失败状态 photo={}", photoId, inner);
                }
                channel.basicNack(deliveryTag, false, false);
            }
        } finally {
            if (traceId != null) MDC.remove("traceId");
        }
    }

    /** 从 x-death header 中读取当前重试次数 */
    @SuppressWarnings("unchecked")
    private int getRetryCount(Message msg) {
        List<Map<String, ?>> deaths = (List<Map<String, ?>>) msg.getMessageProperties()
                .getHeaders().get("x-death");
        if (deaths == null || deaths.isEmpty()) return 0;
        // 每次 requeue/nack 会增加一个 x-death 条目
        return deaths.size();
    }
}
