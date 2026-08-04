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
import java.nio.file.Files;
import java.nio.file.Path;
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
            @RequestBody(required = false) BackupExportRequest req) throws IOException {
        BackupExportRequest request = req != null ? req : new BackupExportRequest();

        // 仅「全量导出」（无任何筛选）可命中预生成缓存；带筛选参数的请求始终实时打包
        boolean fullExport = request.getAlbumId() == null && request.getCategoryId() == null
                && request.getDateFrom() == null && request.getDateTo() == null;

        // 缓存命中：数据指纹未变 → 直接流式返回预生成的 zip（零打包，毫秒级）
        if (fullExport && backupService.isCacheFresh()) {
            Path cache = backupService.getCacheFile();
            log.info("备份导出命中缓存: {} ({} bytes)", cache, Files.size(cache));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + "photo-gallery-backup-" + LocalDate.now() + ".zip\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(cache))
                    .cacheControl(CacheControl.noStore()) // 备份含全部照片，禁止 HTTP 缓存
                    .body(out -> Files.copy(cache, out));
        }

        // 请求线程内同步收集：筛选照片 + 懒加载初始化 + 空结果抛 400。
        // 必须在流式阶段之前完成——响应状态码一旦提交就无法再返回错误响应。
        BackupBundle bundle = backupService.collect(request);

        // 全量导出顺手刷新缓存（下次导出直接命中；失败不影响本次响应）
        if (fullExport) {
            try {
                backupService.updateCache(bundle);
            } catch (IOException e) {
                log.warn("备份缓存刷新失败: {}", e.getMessage());
            }
        }

        String filename = "photo-gallery-backup-" + LocalDate.now() + ".zip";
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
