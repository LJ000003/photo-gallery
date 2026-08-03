package com.hape.photogallery.controller;

import com.hape.photogallery.dto.BackupExportRequest;
import com.hape.photogallery.service.BackupService;
import com.hape.photogallery.service.BackupService.BackupBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.LocalDate;

/**
 * 备份导出（仅 admin，SecurityConfig anyRequest 兜底；viewer 无 POST 权限）。
 * 元数据收集在请求线程同步完成（事务 + 懒加载 + 空结果 400），
 * StreamingResponseBody 异步线程只做文件 I/O 流式打包，不占应用内存。
 */
@RestController
@RequestMapping("/api/v1")
public class BackupController {

    private static final Logger log = LoggerFactory.getLogger(BackupController.class);

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @PostMapping("/backup/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestBody(required = false) BackupExportRequest req) {
        BackupExportRequest request = req != null ? req : new BackupExportRequest();

        // 请求线程内同步收集：筛选照片 + 懒加载初始化 + 空结果抛 400。
        // 必须在流式阶段之前完成——响应状态码一旦提交就无法再返回错误响应。
        BackupBundle bundle = backupService.collect(request);

        String filename = "photo-gallery-backup-" + LocalDate.now() + ".tar.gz";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore()) // 备份含全部照片，禁止缓存
                .body(out -> {
                    try {
                        backupService.writeTo(out, bundle);
                    } catch (IOException e) {
                        // 客户端断开（Broken pipe）或磁盘异常：响应已提交无法重试，记录日志
                        log.warn("备份导出流写入中断: {}", e.getMessage());
                    }
                });
    }
}
