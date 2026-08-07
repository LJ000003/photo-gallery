package com.hape.photogallery.controller;

import com.hape.photogallery.ApiResponse;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.service.PhotoQueryService;

import io.swagger.v3.oas.annotations.Operation;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ShareController {

    private final PhotoQueryService photoQueryService;

    public ShareController(PhotoQueryService photoQueryService) {
        this.photoQueryService = photoQueryService;
    }

    /** 分享落地面 — 转发到 SPA（前端 JS 读取 URL 中的 token） */
    @GetMapping("/share/{token}")
    public ModelAndView sharePage(@PathVariable String token) {
        return new ModelAndView("forward:/index.html");
    }

    /** 分享页 API — 返回 JWT claims 中指定的照片 */
    @Operation(summary = "分享照片列表",
            description = "viewer JWT 白名单校验（JwtAuthFilter 写入 sharePhotoIds）；响应剥离媒体签名（防 view 权限借签名下载原图）")
    @GetMapping("/api/v1/share/view")
    public ApiResponse<Page<PhotoResponse>> view(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        @SuppressWarnings("unchecked")
        List<Long> photoIds = (List<Long>) request.getAttribute("sharePhotoIds");
        if (photoIds == null || photoIds.isEmpty()) {
            throw new BusinessException(404, "分享链接无效或已过期");
        }

        // page/size 钳制——size=-1 曾直接 500（PageRequest 校验抛 IllegalArgumentException）
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.max(1, Math.min(100, size));
        Page<PhotoResponse> result = photoQueryService
                .findByIdsResponses(photoIds, PageRequest.of(clampedPage, clampedSize));
        // 分享上下文不得签发管理员短时签名（否则 view 权限可借签名下载原图），统一剥离
        result.getContent().forEach(r -> r.setMediaToken(null));
        return ApiResponse.success(result);
    }
}
