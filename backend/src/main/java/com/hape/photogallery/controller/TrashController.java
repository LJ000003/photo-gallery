package com.hape.photogallery.controller;

import java.util.List;
import java.util.Map;

import com.hape.photogallery.ApiResponse;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.entity.Album;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.service.AlbumService;
import com.hape.photogallery.service.PhotoService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trash")
public class TrashController {

    private final PhotoService photoService;
    private final AlbumService albumService;

    public TrashController(PhotoService photoService, AlbumService albumService) {
        this.photoService = photoService;
        this.albumService = albumService;
    }

    @GetMapping("/photos")
    public ApiResponse<Page<PhotoResponse>> listPhotos(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(photoService.listDeleted(pageable).map(photoService::toResponse));
    }

    @PostMapping("/photos/{id}/restore")
    public ApiResponse<String> restorePhoto(@PathVariable Long id) {
        photoService.restore(id);
        return ApiResponse.success("恢复成功");
    }

    @DeleteMapping("/photos/{id}")
    public ApiResponse<String> permanentlyDeletePhoto(@PathVariable Long id) {
        photoService.permanentlyDelete(id);
        return ApiResponse.success("已彻底删除");
    }

    @GetMapping("/albums")
    public ApiResponse<List<Album>> listAlbums() {
        return ApiResponse.success(albumService.listDeleted());
    }

    @PostMapping("/albums/{id}/restore")
    public ApiResponse<String> restoreAlbum(@PathVariable Long id) {
        albumService.restore(id);
        return ApiResponse.success("恢复成功");
    }

    @DeleteMapping("/albums/{id}")
    public ApiResponse<String> permanentlyDeleteAlbum(@PathVariable Long id) {
        albumService.permanentlyDelete(id);
        return ApiResponse.success("已彻底删除");
    }
}
