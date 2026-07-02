package com.example.demo;

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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class PhotoController {

    private final PhotoService service;

    public PhotoController(PhotoService service) {
        this.service = service;
    }

    // === 照片 ===

    @GetMapping("/photos")
    public ApiResponse<Page<Photo>> list(
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) List<Long> categoryIds,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(service.listAll(tagIds, categoryIds, pageable));
    }

    @GetMapping("/photos/{id}")
    public Photo get(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping("/photos")
    public Photo upload(@RequestParam("file") MultipartFile file,
                        @RequestParam(value = "name", required = false) String name,
                        @RequestParam(value = "description", required = false) String description,
                        @RequestParam(value = "tagIds", required = false) List<Long> tagIds,
                        @RequestParam(value = "categoryId", required = false) Long categoryId)
            throws IOException {
        return service.upload(file, name, description, tagIds, categoryId);
    }

    @PostMapping("/photos/batch")
    public ApiResponse<List<Photo>> batchUpload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tagIds", required = false) List<Long> tagIds,
            @RequestParam(value = "categoryId", required = false) Long categoryId)
            throws IOException {
        return ApiResponse.success(service.batchUpload(files, name, description, tagIds, categoryId));
    }

    @PutMapping("/photos/{id}")
    public Photo update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        @SuppressWarnings("unchecked")
        List<Long> tagIds = body.get("tagIds") != null
                ? ((List<Integer>) body.get("tagIds")).stream().map(Integer::longValue).toList()
                : null;
        Long categoryId = body.get("categoryId") != null
                ? ((Number) body.get("categoryId")).longValue()
                : null;
        return service.update(id, name, description, tagIds, categoryId);
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

    @GetMapping("/photos/{id}/thumbnail")
    public ResponseEntity<Resource> getThumbnail(@PathVariable Long id) {
        Photo photo = service.getById(id);
        Resource resource = new FileSystemResource(service.getThumbnailPath(id));
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

    @GetMapping("/photos/timeline")
    public ApiResponse<List<ExifData>> timeline(
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return ApiResponse.success(service.getTimeline(sortOrder));
    }

    @GetMapping("/photos/map")
    public ApiResponse<List<ExifData>> mapPhotos() {
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

    // === 标签 ===

    @GetMapping("/tags")
    public ApiResponse<List<Tag>> listTags() {
        return ApiResponse.success(service.listTags());
    }

    @PostMapping("/tags")
    public ApiResponse<Tag> createTag(@RequestBody Map<String, String> body) {
        Tag tag = service.createTag(body.get("name"), body.get("color"));
        return ApiResponse.success(tag);
    }

    @DeleteMapping("/tags/{id}")
    public ApiResponse<String> deleteTag(@PathVariable Long id) {
        service.deleteTag(id);
        return ApiResponse.success("删除成功");
    }

    @PutMapping("/tags/{id}")
    public ApiResponse<Tag> updateTag(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.success(service.updateTag(id, body.get("name"), body.get("color")));
    }

    // === 分类 ===

    @GetMapping("/categories")
    public ApiResponse<List<Category>> listCategories() {
        return ApiResponse.success(service.listCategories());
    }

    @PostMapping("/categories")
    public ApiResponse<Category> createCategory(@RequestBody Map<String, String> body) {
        Category cat = service.createCategory(body.get("name"));
        return ApiResponse.success(cat);
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<String> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ApiResponse.success("删除成功");
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<Category> updateCategory(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.success(service.updateCategory(id, body.get("name")));
    }
}
