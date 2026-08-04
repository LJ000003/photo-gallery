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
        // target 仅供 dev 的 AsyncImageProcessor 使用；消息体只带 photoId，
        // consumer 端通过 photo.fileName 定位文件（targetPath 曾把服务器绝对路径塞进消息，已移除）
        ProcessingMessage msg = new ProcessingMessage(photoId, dateDir, baseName, watermark);

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
