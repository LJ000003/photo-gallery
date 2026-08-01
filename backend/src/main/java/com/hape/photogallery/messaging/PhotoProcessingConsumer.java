package com.hape.photogallery.messaging;

import java.io.IOException;
import java.nio.file.Path;

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
import com.hape.photogallery.service.StorageService;
import com.rabbitmq.client.Channel;

@Component
@ConditionalOnProperty(name = "photo.processing.type", havingValue = "rabbitmq")
public class PhotoProcessingConsumer {

    private static final Logger log = LoggerFactory.getLogger(PhotoProcessingConsumer.class);
    private static final int MAX_RETRIES = 3;

    private final PhotoProcessor processor;
    private final PhotoRepository photoRepo;
    private final StorageService storage;

    public PhotoProcessingConsumer(PhotoProcessor processor, PhotoRepository photoRepo,
                                   StorageService storage) {
        this.processor = processor;
        this.photoRepo = photoRepo;
        this.storage = storage;
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

            // 通过 StorageService 拼接路径（避免依赖 Producer 的绝对路径，支持跨节点消费）
            Path target = storage.getUploadDir().resolve(photo.getFileName());
            processor.process(photoId, target, msg.getDateDir(), msg.getBaseName(), msg.getWatermark());

            // 成功 → ack
            channel.basicAck(deliveryTag, false);
        } catch (Throwable e) {
            int retryCount = getRetryCount(amqpMsg);
            if (retryCount < MAX_RETRIES) {
                log.warn("处理失败（第 {} 次重试）photo={}: {}", retryCount + 1, photoId, e.getMessage());
                // 递增自定义重试计数 header（x-death 在 requeue 路径下不会生成）
                amqpMsg.getMessageProperties().setHeader("x-retry-count", retryCount + 1);
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

    /** 从自定义 header 中读取当前重试次数（x-death 在 basicNack(requeue=true) 路径下不会生成） */
    private int getRetryCount(Message msg) {
        Object count = msg.getMessageProperties().getHeaders().get("x-retry-count");
        if (count instanceof Number n) return n.intValue();
        return 0;
    }
}
