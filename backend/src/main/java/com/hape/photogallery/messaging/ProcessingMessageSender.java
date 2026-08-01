package com.hape.photogallery.messaging;

import java.nio.file.Path;

/**
 * 图片处理消息发送者接口。
 * dev: AsyncProcessingSender（线程池 @Async）
 * prod: RabbitProcessingSender（RabbitMQ）
 */
public interface ProcessingMessageSender {

    /** 发送处理请求。调用方需确保在事务提交后调用（如 afterCommit 回调中） */
    void send(Long photoId, Path target, String dateDir, String baseName, String watermark);
}
