package com.hape.photogallery.messaging;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.hape.photogallery.config.RabbitMQConfig;

/**
 * prod 环境的图片处理发送者 — 通过 RabbitMQ 发送消息。
 * 仅在 photo.processing.type=rabbitmq 时激活。
 */
@Component
@ConditionalOnProperty(name = "photo.processing.type", havingValue = "rabbitmq")
public class RabbitProcessingSender implements ProcessingMessageSender {

    private static final Logger log = LoggerFactory.getLogger(RabbitProcessingSender.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitProcessingSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void send(Long photoId, Path target, String dateDir, String baseName, String watermark) {
        // 转 Path → String（Path 不可序列化）
        String targetPath = target.toString();
        // 截取相对路径（去掉 upload-dir 前缀），consumer 端拼接完整路径
        // 这里直接用 target.toString() 会包含完整路径，需要在 consumer 端用 storageService 解析。
        // 简单处理：取 fileName 作为相对路径标识，consumer 用 photo.fileName 找到文件。

        ProcessingMessage msg = new ProcessingMessage(photoId, targetPath, dateDir, baseName, watermark);

        // 传播 traceId
        String traceId = MDC.get("traceId");
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, msg, m -> {
            if (traceId != null) {
                m.getMessageProperties().setHeader("X-Trace-Id", traceId);
            }
            return m;
        });

        log.debug("发送处理消息 photo={} traceId={}", photoId, traceId);
    }
}
