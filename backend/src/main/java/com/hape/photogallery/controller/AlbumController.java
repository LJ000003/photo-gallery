package com.hape.photogallery.controller;

import java.util.List;
import java.util.Map;

import com.hape.photogallery.ApiResponse;
import com.hape.photogallery.config.MediaSignatureService;
import com.hape.photogallery.dto.AlbumRequest;
import com.hape.photogallery.dto.AlbumResponse;
import com.hape.photogallery.dto.PhotoResponse;
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
    private final MediaSignatureService mediaSignature;

    public AlbumController(AlbumService albumService, MediaSignatureService mediaSignature) {
        this.albumService = albumService;
        this.mediaSignature = mediaSignature;
    }

    @GetMapping("/albums")
    public ApiResponse<List<AlbumResponse>> listAlbums() {
        List<AlbumResponse> albums = albumService.listAll();
        // 封面图短时签名：仅列表渲染需要，创建/更新响应不需要
        for (AlbumResponse a : albums) {
            if (a.getCoverPhotoId() != null) {
                a.setMediaToken(mediaSignature.sign(a.getCoverPhotoId()));
            }
        }
        return ApiResponse.success(albums);
    }

    /** 相册详情（补缺端点：此前 GET /albums/{id} 405 → 兜底 500「系统繁忙」） */
    @GetMapping("/albums/{id}")
    public ApiResponse<AlbumResponse> getAlbum(@PathVariable Long id) {
        AlbumResponse album = albumService.getAlbum(id);
        if (album.getCoverPhotoId() != null) {
            album.setMediaToken(mediaSignature.sign(album.getCoverPhotoId()));
        }
        return ApiResponse.success(album);
    }

    @PostMapping("/albums")
    public ApiResponse<AlbumResponse> createAlbum(@Valid @RequestBody AlbumRequest req) {
        return ApiResponse.success(albumService.create(req.getName(), req.getDescription(), req.getPhotoIds()));
    }

    @PutMapping("/albums/{id}")
    public ApiResponse<AlbumResponse> updateAlbum(@PathVariable Long id, @Valid @RequestBody AlbumRequest req) {
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
    public ApiResponse<Page<PhotoResponse>> listAlbumPhotos(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        // 走 PhotoResponse（携带图片短时签名），实体直接序列化会让相册详情缩略图无鉴权；
        // 事务内 map（懒加载代理序列化防护）
        return ApiResponse.success(albumService.listPhotosResponses(id, pageable));
    }

    @GetMapping("/albums/{id}/photo-ids")
    public ApiResponse<List<Long>> listAlbumPhotoIds(@PathVariable Long id) {
        // 轻量投影（只返回 id），供编辑抽屉预选初始化，避免传输完整 PhotoResponse 的 N+1
        return ApiResponse.success(albumService.listPhotoIds(id));
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
