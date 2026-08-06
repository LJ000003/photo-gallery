package com.hape.photogallery.controller;

import java.util.List;
import java.util.Map;

import com.hape.photogallery.ApiResponse;
import com.hape.photogallery.dto.AlbumResponse;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.service.AlbumService;
import com.hape.photogallery.service.PhotoQueryService;
import com.hape.photogallery.service.TrashService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trash")
public class TrashController {

    private final TrashService trashService;
    private final PhotoQueryService photoQueryService;
    private final AlbumService albumService;

    public TrashController(TrashService trashService, PhotoQueryService photoQueryService,
                           AlbumService albumService) {
        this.trashService = trashService;
        this.photoQueryService = photoQueryService;
        this.albumService = albumService;
    }

    @GetMapping("/photos")
    public ApiResponse<Page<PhotoResponse>> listPhotos(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(trashService.listDeleted(pageable).map(photoQueryService::toResponse));
    }

    @PostMapping("/photos/{id}/restore")
    public ApiResponse<String> restorePhoto(@PathVariable Long id) {
        trashService.restore(id);
        return ApiResponse.success("恢复成功");
    }

    @DeleteMapping("/photos/{id}")
    public ApiResponse<String> permanentlyDeletePhoto(@PathVariable Long id) {
        trashService.permanentlyDelete(id);
        return ApiResponse.success("已彻底删除");
    }

    @GetMapping("/albums")
    public ApiResponse<List<AlbumResponse>> listAlbums() {
        // DTO 化（photoCount 恒 0，回收站 UI 不显示计数）
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
