package com.hape.photogallery.controller;

import java.util.List;
import java.util.Map;

import com.hape.photogallery.ApiResponse;
import com.hape.photogallery.dto.AlbumRequest;
import com.hape.photogallery.entity.Album;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.service.AlbumService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AlbumController {

    private final AlbumService albumService;

    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }

    @GetMapping("/albums")
    public ApiResponse<List<Album>> listAlbums() {
        return ApiResponse.success(albumService.listAll());
    }

    @PostMapping("/albums")
    public ApiResponse<Album> createAlbum(@Valid @RequestBody AlbumRequest req) {
        return ApiResponse.success(albumService.create(req.getName(), req.getDescription(), req.getPhotoIds()));
    }

    @PutMapping("/albums/{id}")
    public ApiResponse<Album> updateAlbum(@PathVariable Long id, @Valid @RequestBody AlbumRequest req) {
        return ApiResponse.success(albumService.update(id, req.getName(), req.getDescription(), req.getPhotoIds()));
    }

    @DeleteMapping("/albums/{id}")
    public ApiResponse<String> deleteAlbum(@PathVariable Long id) {
        albumService.delete(id);
        return ApiResponse.success("删除成功");
    }

    @PostMapping("/albums/{id}/restore")
    public ApiResponse<String> restoreAlbum(@PathVariable Long id) {
        albumService.restore(id);
        return ApiResponse.success("恢复成功");
    }

    @GetMapping("/albums/{id}/photos")
    public ApiResponse<Page<Photo>> listAlbumPhotos(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(albumService.listPhotos(id, pageable));
    }

    @PostMapping("/albums/{id}/photos")
    public ApiResponse<String> addPhotosToAlbum(@PathVariable Long id, @RequestBody List<Long> photoIds) {
        albumService.addPhotos(id, photoIds);
        return ApiResponse.success("ok");
    }

    @DeleteMapping("/albums/{id}/photos")
    public ApiResponse<String> removePhotosFromAlbum(@PathVariable Long id, @RequestBody List<Long> photoIds) {
        albumService.removePhotos(id, photoIds);
        return ApiResponse.success("ok");
    }
}
