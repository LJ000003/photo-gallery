package com.hape.photogallery.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时异步补生成缺失的缩略图/WebP（MigrationService.backfillMissingFilesOnStartup 是 @Async，
 * 跨 bean 调用代理生效，不阻塞应用启动与端口监听）。
 * 场景：历史数据/迁移遗留——照片 DONE 但缩略图或 WebP 文件缺失（画廊会回退下载原图，首屏极慢）。
 * 开关 photo.startup-backfill.enabled（默认开），照片量大或 IO 紧张的机器可关闭。
 */
@Component
public class StartupBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupBackfillRunner.class);

    private final MigrationService migrationService;
    private final boolean enabled;

    public StartupBackfillRunner(MigrationService migrationService,
                                 @Value("${photo.startup-backfill.enabled:true}") boolean enabled) {
        this.migrationService = migrationService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("启动补生成已关闭（photo.startup-backfill.enabled=false）");
            return;
        }
        migrationService.backfillMissingFilesOnStartup();
    }
}
