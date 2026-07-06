package com.hape.photogallery;

import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.PhotoUpdateRequest;
import com.hape.photogallery.dto.TimelineItem;

import jakarta.validation.Valid;

import java.io.IOException;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class PhotoController {

    private final PhotoService service;
    private final AlbumService albumService;

    public PhotoController(PhotoService service, AlbumService albumService) {
        this.service = service;
        this.albumService = albumService;
    }

    // === 照片 ===

    @GetMapping("/photos")
    public ApiResponse<Page<PhotoResponse>> list(
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long albumId,
            @PageableDefault(size = 20) Pageable pageable) {
        if (q != null && !q.isBlank()) {
            return ApiResponse.success(service.search(q, pageable).map(service::toResponse));
        }
        if (albumId != null) {
            Page<Photo> page = albumId == 0
                    ? albumService.listUnassigned(pageable)
                    : albumService.listPhotos(albumId, pageable);
            return ApiResponse.success(page.map(service::toResponse));
        }
        return ApiResponse.success(service.listAll(tagIds, categoryIds, pageable).map(service::toResponse));
    }

    @GetMapping("/photos/{id}")
    public PhotoResponse get(@PathVariable Long id) {
        return service.toResponse(service.getById(id));
    }

    @PostMapping("/photos")
    public PhotoResponse upload(@RequestParam("file") MultipartFile file,
                        @RequestParam(value = "name", required = false) String name,
                        @RequestParam(value = "description", required = false) String description,
                        @RequestParam(value = "tagIds", required = false) List<Long> tagIds,
                        @RequestParam(value = "categoryId", required = false) Long categoryId,
                        @RequestParam(value = "watermark", required = false) String watermark)
            throws IOException {
        return service.toResponse(service.upload(file, name, description, tagIds, categoryId, watermark));
    }

    @PostMapping("/photos/batch")
    public ApiResponse<List<PhotoResponse>> batchUpload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tagIds", required = false) List<Long> tagIds,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "watermark", required = false) String watermark)
            throws IOException {
        List<PhotoResponse> result = service.batchUpload(files, name, description, tagIds, categoryId, watermark)
                .stream().map(service::toResponse).toList();
        return ApiResponse.success(result);
    }

    @PutMapping("/photos/{id}")
    public PhotoResponse update(@PathVariable Long id,
                                 @Valid @RequestBody PhotoUpdateRequest body) {
        return service.update(id, body);
    }

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

    @GetMapping("/photos/{id}/file")
    public ResponseEntity<Resource> getFile(@PathVariable Long id) {
        Photo photo = service.getById(id);
        Resource resource = new FileSystemResource(service.getFilePath(id));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .body(resource);
    }

    @GetMapping("/photos/{id}/webp")
    public ResponseEntity<Resource> getWebp(@PathVariable Long id) {
        Resource resource = new FileSystemResource(service.getWebpPath(id));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .body(resource);
    }

    @GetMapping("/photos/{id}/thumbnail")
    public ResponseEntity<Resource> getThumbnail(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "400") int w) {
        Photo photo = service.getById(id);
        Resource resource = new FileSystemResource(service.getThumbnailPath(id, w));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS))
                .body(resource);
    }

    @PostMapping("/photos/migrate-thumbnails")
    public ApiResponse<Map<String, Integer>> migrateThumbnails() {
        int count = service.migrateThumbnails();
        return ApiResponse.success(Map.of("generated", count));
    }

    @PostMapping("/photos/migrate-webp")
    public ApiResponse<Map<String, Integer>> migrateWebp() {
        int count = service.migrateWebp();
        return ApiResponse.success(Map.of("generated", count));
    }

    @GetMapping("/photos/timeline")
    public ApiResponse<List<TimelineItem>> timeline(
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return ApiResponse.success(service.getTimeline(sortOrder));
    }

    @GetMapping("/photos/map")
    public ApiResponse<List<MapItem>> mapPhotos() {
        return ApiResponse.success(service.getMapPhotos());
    }

    @PostMapping("/photos/extract-exif")
    public ApiResponse<Map<String, Integer>> extractExifBatch() {
        int count = service.extractExifForExisting();
        return ApiResponse.success(Map.of("extracted", count));
    }

    @PostMapping("/photos/{id}/extract-exif")
    public ApiResponse<ExifData> extractExif(@PathVariable Long id) {
        return ApiResponse.success(service.extractExifForPhoto(id));
    }

    @PostMapping("/photos/{id}/transform")
    public ApiResponse<String> transform(@PathVariable Long id, @RequestBody Map<String, Object> body)
            throws IOException {
        int rotate = body.get("rotate") != null ? ((Number) body.get("rotate")).intValue() : 0;
        String mirror = (String) body.getOrDefault("mirror", "none");
        Double cx = body.get("cx") != null ? ((Number) body.get("cx")).doubleValue() : null;
        Double cy = body.get("cy") != null ? ((Number) body.get("cy")).doubleValue() : null;
        Double cw = body.get("cw") != null ? ((Number) body.get("cw")).doubleValue() : null;
        Double ch = body.get("ch") != null ? ((Number) body.get("ch")).doubleValue() : null;
        service.transformPhoto(id, rotate, mirror, cx, cy, cw, ch);
        return ApiResponse.success("ok");
    }
}
