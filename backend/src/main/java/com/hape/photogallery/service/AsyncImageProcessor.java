package com.hape.photogallery.service;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.hape.photogallery.messaging.ProcessingMessageSender;

/**
 * dev/test 环境的图片处理发送者 — 通过 @Async 线程池直接执行。
 * 仅在 photo.processing.type=async（默认）时激活。
 */
@Component
@ConditionalOnProperty(name = "photo.processing.type", havingValue = "async", matchIfMissing = true)
public class AsyncImageProcessor implements ProcessingMessageSender {

    private static final Logger log = LoggerFactory.getLogger(AsyncImageProcessor.class);

    private final PhotoProcessor processor;

    public AsyncImageProcessor(PhotoProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void send(Long photoId, Path target, String dateDir, String baseName, String watermark) {
        asyncProcess(photoId, target, dateDir, baseName, watermark);
    }

    @Async("imageTaskExecutor")
    public void asyncProcess(Long photoId, Path target, String dateDir, String baseName, String watermark) {
        processor.process(photoId, target, dateDir, baseName, watermark);
    }
}
