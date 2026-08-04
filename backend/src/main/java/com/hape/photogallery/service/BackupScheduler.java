package com.hape.photogallery.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 备份预生成定时任务：每天自动打包一份全量备份缓存，
 * 用户导出时若数据指纹未变（BackupService.isCacheFresh）直接下载缓存，避免实时打包等待。
 * 独立 Bean（而非写在 BackupService 内）——跨 Bean 调用 collect() 才能走 @Transactional 代理。
 */
@Component
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;

    public BackupScheduler(BackupService backupService) {
        this.backupService = backupService;
    }

    @Scheduled(cron = "${backup.auto-cron:0 5 3 * * ?}", zone = "Asia/Shanghai")
    public void autoBackup() {
        if (backupService.generateCachedBackup()) {
            log.info("定时备份缓存生成完成");
        }
    }
}
