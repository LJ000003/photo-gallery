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
import com.hape.photogallery.entity.ProcessingStatus;
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
    private final RabbitTemplate rabbitTemplate;

    public PhotoProcessingConsumer(PhotoProcessor processor, PhotoRepository photoRepo,
                                   StorageService storage, RabbitTemplate rabbitTemplate) {
        this.processor = processor;
        this.photoRepo = photoRepo;
        this.storage = storage;
        this.rabbitTemplate = rabbitTemplate;
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
                // 显式重投到 TTL 重试队列（30s 后死信回主队列再尝试），先投后 ack（at-least-once，
                // processor 幂等可重入，重复仅多一次处理）。
                // 计数用自定义 header x-retry-count：显式重投是重新发布消息，header 随消息持久化
                // （AMQP 消息语义，与 broker 版本无关——X-Trace-Id 在 TTL 死信后保留已实测）。
                // 为何不用 broker 生成的 x-death：实测 RabbitMQ 4.x 死信时 x-death 被重置为 1 而非
                // 「同组合合并递增」，依赖它会导致重试计数恒 1、消息无限循环。
                // 原 bug 根因：basicNack(requeue=true) 回投的是 broker 原始消息，本地 header 改动不持久。
                // TTL 是消息级 expiration（队列无 x-message-ttl 参数）：死信重发布时 RabbitMQ
                // 会剥离 expiration 不二次生效；调整 RETRY_TTL_MS 无需删队列（曾因队列级 TTL
                // 参数不可变导致 406 PRECONDITION_FAILED，部署需手动删旧队列）。
                amqpMsg.getMessageProperties().setHeader("x-retry-count", retryCount + 1);
                amqpMsg.getMessageProperties()
                        .setExpiration(String.valueOf(RabbitMQConfig.RETRY_TTL_MS));
                rabbitTemplate.send(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RETRY_ROUTING_KEY, amqpMsg);
                channel.basicAck(deliveryTag, false);
            } else {
                log.error("处理失败（已达最大重试 {} 次）photo={}，转入 DLQ", MAX_RETRIES, photoId, e);
                // 标记 FAILED + 不 requeue → 消息进入 DLQ
                try {
                    Photo photo = photoRepo.findById(photoId).orElse(null);
                    if (photo != null) {
                        String errMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                        if (errMsg.length() > 500) errMsg = errMsg.substring(0, 497) + "...";
                        photo.setProcessingStatus(ProcessingStatus.FAILED);
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

    /** 读自定义 x-retry-count header（随显式重投持久化）；初投消息无此 header → 0 */
    private int getRetryCount(Message msg) {
        Object count = msg.getMessageProperties().getHeaders().get("x-retry-count");
        if (count instanceof Number n) return n.intValue();
        return 0;
    }
}
