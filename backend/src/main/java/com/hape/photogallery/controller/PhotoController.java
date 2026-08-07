package com.hape.photogallery.controller;

import com.hape.photogallery.ApiResponse;
import com.hape.photogallery.dto.BatchPhotoUpdateRequest;
import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.PhotoProcessingStatusDto;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.PhotoUpdateRequest;
import com.hape.photogallery.dto.TimelineItem;
import com.hape.photogallery.dto.TransformRequest;
import com.hape.photogallery.dto.UploadParams;
import com.hape.photogallery.entity.ExifData;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.exception.DuplicateException;
import com.hape.photogallery.service.AlbumService;
import com.hape.photogallery.service.FilePathResolver;
import com.hape.photogallery.service.MigrationService;
import com.hape.photogallery.service.PhotoQueryService;
import com.hape.photogallery.service.PhotoService;
import com.hape.photogallery.service.PhotoTransformService;
import com.hape.photogallery.service.TrashService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class PhotoController {

    private final PhotoService service;
    private final PhotoQueryService photoQueryService;
    private final TrashService trashService;
    private final AlbumService albumService;
    private final MigrationService migrationService;
    private final FilePathResolver filePathResolver;
    private final PhotoTransformService transformService;

    public PhotoController(PhotoService service, PhotoQueryService photoQueryService,
                           TrashService trashService, AlbumService albumService,
                           MigrationService migrationService, FilePathResolver filePathResolver,
                           PhotoTransformService transformService) {
        this.service = service;
        this.photoQueryService = photoQueryService;
        this.trashService = trashService;
        this.albumService = albumService;
        this.migrationService = migrationService;
        this.filePathResolver = filePathResolver;
        this.transformService = transformService;
    }

    // === 照片 ===

    @Operation(summary = "分页查询照片",
            description = "支持 sort 排序（createdAt/fileSize/name 白名单）、标签/分类筛选、FULLTEXT 全文搜索（单字或非 MySQL 数据库走 LIKE fallback）；albumId=0 表示未分配相册")
    @GetMapping("/photos")
    public ApiResponse<Page<PhotoResponse>> list(
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long albumId,
            @PageableDefault(size = 20) Pageable pageable) {
        if (q != null && !q.isBlank()) {
            // 搜索与标签/分类筛选可组合（交集），不再忽略筛选条件；
            // 事务内 map（懒加载代理序列化防护）
            return ApiResponse.success(photoQueryService.searchResponses(q, tagIds, categoryIds, pageable));
        }
        if (albumId != null) {
            // 事务内 map（懒加载代理序列化防护）
            Page<PhotoResponse> page = albumId == 0
                    ? albumService.listUnassignedResponses(pageable)
                    : albumService.listPhotosResponses(albumId, pageable);
            return ApiResponse.success(page);
        }
        return ApiResponse.success(photoQueryService.listAllResponses(tagIds, categoryIds, pageable));
    }

    @Operation(summary = "照片详情", description = "含 EXIF、标签、分类、相册与媒体签名")
    @GetMapping("/photos/{id}")
    public ApiResponse<PhotoResponse> get(@PathVariable Long id) {
        return ApiResponse.success(photoQueryService.getPhotoResponse(id));
    }

    /** 批量处理状态（前端 3s 轮询专用，轻量 DTO + 不缓存）；字面路径优先于 /photos/{id} */
    @Operation(summary = "批量查询处理状态", description = "只返回 id/processingStatus/errorMessage，供轮询使用")
    @GetMapping("/photos/status")
    public ApiResponse<List<PhotoProcessingStatusDto>> status(@RequestParam List<Long> ids) {
        if (ids.size() > 100) {
            throw new BusinessException(400, "单次最多查询 100 张");
        }
        return ApiResponse.success(photoQueryService.getProcessingStatuses(ids));
    }

    @Operation(summary = "上传单张照片",
            description = "SHA-256 去重（重复返回 409 + 已有照片数据）；魔数校验；异步图片处理管线",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                            description = "文件已存在，data 中携带已有照片")
            })
    @PostMapping("/photos")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                        @RequestParam(value = "name", required = false) String name,
                        @RequestParam(value = "description", required = false) String description,
                        @RequestParam(value = "tagIds", required = false) List<Long> tagIds,
                        @RequestParam(value = "categoryId", required = false) Long categoryId,
                        @RequestParam(value = "watermark", required = false) String watermark)
            throws IOException {
        try {
            UploadParams params = new UploadParams(
                    fixMultipartText(name), fixMultipartText(description), tagIds, categoryId, fixMultipartText(watermark));
            return ResponseEntity.ok(ApiResponse.success(photoQueryService.toResponse(service.upload(file, params))));
        } catch (DuplicateException e) {
            return ResponseEntity.status(409).body(ApiResponse.error(409, e.getMessage(), e.getExisting()));
        }
    }

    private static final int MAX_BATCH_SIZE = 50;

    @PostMapping("/photos/batch")
    public ApiResponse<List<PhotoResponse>> batchUpload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tagIds", required = false) List<Long> tagIds,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "watermark", required = false) String watermark)
            throws IOException {
        if (files.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(400, "单次最多上传 " + MAX_BATCH_SIZE + " 张图片");
        }
        UploadParams params = new UploadParams(
                fixMultipartText(name), fixMultipartText(description), tagIds, categoryId, fixMultipartText(watermark));
        List<PhotoResponse> result = service.batchUpload(files, params)
                .stream().map(photoQueryService::toResponse).toList();
        return ApiResponse.success(result);
    }

    /**
     * multipart 文本参数编码恢复：浏览器 FormData 的文本 part 不带 Content-Type charset，
     * Tomcat 10 按 RFC 7578 用 ISO-8859-1 解码 UTF-8 字节 → 中文乱码（实测 curl -F /
     * 裸 FormData 复现；query 参数与带 charset 的 part 不受影响）。
     * 恢复逻辑带保护：仅当参数含 U+0080-U+00FF（ISO-8859-1 解码 UTF-8 的特征）时尝试
     * 重建，且恢复结果含 U+FFFD（无效 UTF-8）则保留原值——纯 ASCII 与合法 ISO-8859-1
     * 文本零误伤。
     */
    static String fixMultipartText(String s) {
        if (s == null || s.isEmpty()) return s;
        boolean highByte = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x80 && c <= 0xFF) {
                highByte = true;
                break;
            }
        }
        if (!highByte) return s;
        try {
            String recovered = new String(s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                    java.nio.charset.StandardCharsets.UTF_8);
            // 恢复结果出现替换符说明原文本本就不是 UTF-8 字节——保留原值
            return recovered.indexOf('�') >= 0 ? s : recovered;
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * contentType 回退解析：multipart part 无 Content-Type 时落库为 null，
     * parseMediaType(null) 曾抛 IAE → 500，一条脏数据即可让照片永远无法查看。
     */
    private MediaType resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    @Operation(summary = "更新照片",
            description = "null = 不修改；categoryId=0 清除分类；tagIds/albumIds 传数组表示整体替换")
    @PutMapping("/photos/{id}")
    public ApiResponse<PhotoResponse> update(@PathVariable Long id,
                                 @Valid @RequestBody PhotoUpdateRequest body) {
        return ApiResponse.success(service.update(id, body));
    }

    @Operation(summary = "软删除照片", description = "删除后进入回收站（30 天后自动清理），fileHash 清空可重新上传")
    @DeleteMapping("/photos/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功");
    }

    @DeleteMapping("/photos/batch")
    public ApiResponse<Map<String, Integer>> batchDelete(@RequestBody List<Long> ids) {
        int count = service.batchDelete(ids);
        return ApiResponse.success(Map.of("deleted", count));
    }

    @PutMapping("/photos/batch")
    public ApiResponse<List<PhotoResponse>> batchUpdate(@Valid @RequestBody BatchPhotoUpdateRequest body) {
        return ApiResponse.success(service.batchUpdate(body));
    }

    @PostMapping("/photos/{id}/restore")
    public ApiResponse<String> restore(@PathVariable Long id) {
        trashService.restore(id);
        return ApiResponse.success("恢复成功");
    }

    @GetMapping("/photos/{id}/file")
    public ResponseEntity<Resource> getFile(@PathVariable Long id) {
        Photo photo = filePathResolver.getByIdIncludeDeleted(id);
        if (photo.getDeletedAt() != null && !isAdmin()) {
            throw new BusinessException(404, "该照片已被删除");
        }
        Resource resource = new FileSystemResource(filePathResolver.getFilePath(id));
        if (!resource.exists()) {
            throw new BusinessException(404, "文件不存在");
        }
        return ResponseEntity.ok()
                .contentType(resolveContentType(photo.getContentType()))
                .body(resource);
    }

    @GetMapping("/photos/{id}/webp")
    public ResponseEntity<Resource> getWebp(@PathVariable Long id) {
        Photo photo = filePathResolver.getByIdIncludeDeleted(id);
        if (photo.getDeletedAt() != null && !isAdmin()) {
            throw new BusinessException(404, "该照片已被删除");
        }
        MediaType mediaType = MediaType.parseMediaType("image/webp");
        Path path = filePathResolver.getWebpPath(id);
        if (path == null || !Files.exists(path)) {
            // webp 缺失——admin 回退原图（功能无损）；viewer 回退缩略图（不破图，
            // 且不能回退原图——view-only 分享借 /webp 回退下载原图，已封堵）
            if (!isAdmin()) {
                path = filePathResolver.getThumbnailPath(id, 400);
                mediaType = MediaType.IMAGE_JPEG;
                if (path == null || !Files.exists(path)) {
                    throw new BusinessException(404, "图片不存在");
                }
            } else {
                path = filePathResolver.getFilePath(id);
                mediaType = resolveContentType(photo.getContentType()); // 回退分支按原图类型返回
            }
        }
        Resource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new BusinessException(404, "文件不存在");
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(resource);
    }

    @GetMapping("/photos/{id}/thumbnail")
    public ResponseEntity<Resource> getThumbnail(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "400") int w) {
        // w 白名单——全仓仅生成 200/400 两档（PhotoProcessor/AsyncImageProcessor），
        // 任意 w 此前可借回退路径探测磁盘（回退 400 → 原图）
        if (w != 200 && w != 400) {
            throw new BusinessException(400, "缩略图宽度仅支持 200/400");
        }
        Photo photo = filePathResolver.getByIdIncludeDeleted(id);
        if (photo.getDeletedAt() != null && !isAdmin()) {
            throw new BusinessException(404, "该照片已被删除");
        }
        MediaType mediaType = MediaType.IMAGE_JPEG;
        Path path = filePathResolver.getThumbnailPath(id, w);
        if (path == null || !Files.exists(path)) {
            // 缩略图缺失——admin 回退原图（功能无损），viewer 一律 404
            // （此前 view-only 分享可借缩略图缺失回退下载原图，已封堵）
            if (!isAdmin()) {
                throw new BusinessException(404, "缩略图不存在");
            }
            path = filePathResolver.getFilePath(id);
            mediaType = resolveContentType(photo.getContentType()); // 回退分支按原图类型返回
        }
        Resource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new BusinessException(404, "文件不存在");
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS))
                .body(resource);
    }

    @PostMapping("/photos/migrate-thumbnails")
    public ApiResponse<Map<String, Integer>> migrateThumbnails() {
        int count = migrationService.migrateThumbnails();
        return ApiResponse.success(Map.of("generated", count));
    }

    @PostMapping("/photos/migrate-webp")
    public ApiResponse<Map<String, Integer>> migrateWebp() {
        int count = migrationService.migrateWebp();
        return ApiResponse.success(Map.of("generated", count));
    }

    @Operation(summary = "EXIF 拍摄时间线", description = "按拍摄日期年月分组分页")
    @GetMapping("/photos/timeline")
    public ApiResponse<Page<TimelineItem>> timeline(
            @RequestParam(defaultValue = "desc") String sortOrder,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiResponse.success(photoQueryService.getTimeline(sortOrder, pageable));
    }

    @GetMapping("/photos/map")
    public ApiResponse<List<MapItem>> mapPhotos(
            @RequestParam double swLat,
            @RequestParam double swLng,
            @RequestParam double neLat,
            @RequestParam double neLng) {
        return ApiResponse.success(photoQueryService.getMapPhotos(swLat, swLng, neLat, neLng));
    }

    @PostMapping("/photos/extract-exif")
    public ApiResponse<Map<String, Integer>> extractExifBatch() {
        int count = migrationService.extractExifForExisting();
        return ApiResponse.success(Map.of("extracted", count));
    }

    @PostMapping("/photos/{id}/extract-exif")
    public ApiResponse<ExifData> extractExif(@PathVariable Long id) {
        return ApiResponse.success(photoQueryService.extractExifForPhoto(id));
    }

    @PostMapping("/photos/{id}/transform")
    public ApiResponse<String> transform(@PathVariable Long id,
                                         @Valid @RequestBody TransformRequest body) throws IOException {
        transformService.transformPhoto(id, body.getRotate(), body.getMirror(),
                body.getCx(), body.getCy(), body.getCw(), body.getCh());
        return ApiResponse.success("ok");
    }

    @PostMapping("/photos/{id}/retry-processing")
    public ApiResponse<String> retryProcessing(@PathVariable Long id) {
        service.retryProcessing(id);
        return ApiResponse.success("已重新提交处理");
    }

    /**
     * 已删除（回收站）照片的图片只允许 admin 查看，viewer 仍返回 404。
     */
    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_admin".equals(a.getAuthority()));
    }
}
