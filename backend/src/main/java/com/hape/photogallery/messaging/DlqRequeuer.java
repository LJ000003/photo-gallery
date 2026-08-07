package com.hape.photogallery.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hape.photogallery.config.RabbitMQConfig;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.ProcessingStatus;
import com.hape.photogallery.repository.PhotoRepository;

/**
 * DLQ 死信恢复（P0 修复）：consumer 3 次重试耗尽后照片落 FAILED、消息进 DLQ——此前
 * DLQ 无消费者、FAILED 无任何自动恢复路径（5 分钟重扫只扫 PROCESSING、backfill 只扫
 * DONE），瞬时故障（DB 抖动/磁盘满/Rabbit 闪断）会把在途照片批量判死且永不翻回。
 * <p>
 * 每 2 分钟 drain：DLQ 消息取出（basicGet 自动 ack）→ 照片存在且 FAILED 则置回
 * PROCESSING + 重新发布主队列（全新消息，无 x-retry-count，consumer 从 0 重新重试）。
 * 顺序关键：先落 PROCESSING 再发布——发布失败时状态已是 PROCESSING，
 * PhotoService.recoverStuckProcessing（5 分钟重扫）会兜底补发。
 * DONE/不存在/PROCESSING 状态的消息直接丢弃（在途消息或重扫已覆盖）。
 * <p>
 * 已知取舍：确定性失败（如「无法解码」）照片每 ~2 分钟重处理一次循环——该分支不占
 * consumer 重试计数，CPU 开销有界（每张照片一次完整解码），且能在原图被修复/替换后自愈。
 */
@Component
@ConditionalOnProperty(name = "photo.processing.type", havingValue = "rabbitmq")
public class DlqRequeuer {

    private static final Logger log = LoggerFactory.getLogger(DlqRequeuer.class);

    private final RabbitTemplate rabbitTemplate;
    private final PhotoRepository photoRepo;

    public DlqRequeuer(RabbitTemplate rabbitTemplate, PhotoRepository photoRepo) {
        this.rabbitTemplate = rabbitTemplate;
        this.photoRepo = photoRepo;
    }

    @Scheduled(cron = "0 */2 * * * *")
    public void drain() {
        int requeued = 0;
        while (true) {
            Object obj;
            try {
                obj = rabbitTemplate.receiveAndConvert(RabbitMQConfig.DLQ);
            } catch (MessageConversionException e) {
                // 反序列化失败（升级遗留旧格式/损坏消息）：消息已被 basicGet 自动 ack 消费，
                // 无法重投——丢弃并继续 drain（此前逃逸到 catch(Exception) 会中断整轮恢复，
                // 后续正常消息全部得不到重试；FAILED 照片将永久卡死）
                log.warn("DLQ 消息反序列化失败（已消费，无法重投），丢弃: {}", e.getMessage());
                continue;
            } catch (Exception e) {
                // 队列不可达（Rabbit 重启等）：本轮放弃，下轮再试；消息仍在 DLQ（basicGet 失败不会消费）
                log.warn("DLQ drain 失败: {}", e.getMessage());
                return;
            }
            if (obj == null) break; // 空队列

            ProcessingMessage msg;
            try {
                msg = (ProcessingMessage) obj;
            } catch (ClassCastException e) {
                log.warn("DLQ 消息类型不匹配，丢弃: {}", obj);
                continue;
            }
            Photo photo = photoRepo.findById(msg.getPhotoId()).orElse(null);
            if (photo == null) {
                log.warn("DLQ 消息照片不存在，丢弃 photo={}", msg.getPhotoId());
                continue;
            }
            if (photo.getProcessingStatus() != ProcessingStatus.FAILED) {
                log.info("DLQ 消息照片状态非 FAILED（{}），丢弃 photo={}", photo.getProcessingStatus(), msg.getPhotoId());
                continue;
            }

            photo.setProcessingStatus(ProcessingStatus.PROCESSING);
            photo.setErrorMessage(null);
            photoRepo.save(photo);
            try {
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY,
                        new ProcessingMessage(photo.getId(), msg.getDateDir(), msg.getBaseName(), msg.getWatermark()));
                requeued++;
            } catch (Exception e) {
                // 发布失败：状态已落 PROCESSING，5 分钟重扫兜底补发
                log.warn("DLQ 重新入队失败 photo={}（状态已置 PROCESSING，重扫兜底）: {}", msg.getPhotoId(), e.getMessage());
            }
        }
        if (requeued > 0) {
            log.info("DLQ drain 完成：{} 张照片重新入队", requeued);
        }
    }
}
