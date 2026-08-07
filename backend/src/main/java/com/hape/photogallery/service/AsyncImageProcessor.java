package com.hape.photogallery.service;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.ProcessingStatus;
import com.hape.photogallery.messaging.ProcessingMessageSender;
import com.hape.photogallery.repository.PhotoRepository;

/**
 * dev/test 环境的图片处理发送者 — 通过 @Async 线程池直接执行。
 * 仅在 photo.processing.type=async（默认）时激活。
 *
 * PhotoProcessor 失败只抛错不落状态（P2 修复：状态写入上移到调用方，
 * 避免与 rabbit 重试并存时的状态翻转）；本类（无自动重试）负责在
 * 首次失败后落 FAILED 终态，与 prod 的「重试耗尽落 FAILED」语义一致。
 */
@Component
@ConditionalOnProperty(name = "photo.processing.type", havingValue = "async", matchIfMissing = true)
public class AsyncImageProcessor implements ProcessingMessageSender {

    private static final Logger log = LoggerFactory.getLogger(AsyncImageProcessor.class);

    private final PhotoProcessor processor;
    private final PhotoRepository photoRepo;

    public AsyncImageProcessor(PhotoProcessor processor, PhotoRepository photoRepo) {
        this.processor = processor;
        this.photoRepo = photoRepo;
    }

    @Async("imageTaskExecutor")
    @Override
    public void send(Long photoId, Path target, String dateDir, String baseName, String watermark) {
        try {
            processor.process(photoId, target, dateDir, baseName, watermark);
        } catch (Throwable e) {
            // dev 无自动重试，落 FAILED 终态（前端显示重试按钮，可手动 retry-processing）
            try {
                Photo photo = photoRepo.findById(photoId).orElse(null);
                if (photo != null) {
                    String errMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                    if (errMsg.length() > 500) errMsg = errMsg.substring(0, 497) + "...";
                    photo.setProcessingStatus(ProcessingStatus.FAILED);
                    photo.setErrorMessage(errMsg);
                    photoRepo.save(photo);
                }
            } catch (Throwable inner) {
                log.error("无法保存失败状态 photo={}", photoId, inner);
            }
        }
    }
}
