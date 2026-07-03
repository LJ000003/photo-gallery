package com.hape.photogallery;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ShareController {

    private final PhotoService photoService;

    public ShareController(PhotoService photoService) {
        this.photoService = photoService;
    }

    /** 分享落地面 — 转发到 SPA（前端 JS 读取 URL 中的 token） */
    @GetMapping("/share/{token}")
    public ModelAndView sharePage(@PathVariable String token) {
        return new ModelAndView("forward:/index.html");
    }

    /** 分享页 API — 返回 JWT claims 中指定的照片 */
    @GetMapping("/api/share/view")
    public ApiResponse<Page<Photo>> view(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        @SuppressWarnings("unchecked")
        List<Long> photoIds = (List<Long>) request.getAttribute("sharePhotoIds");
        if (photoIds == null || photoIds.isEmpty()) {
            throw new RuntimeException("分享链接无效或已过期");
        }

        Page<Photo> result = photoService.findByIds(photoIds, PageRequest.of(page, size));
        return ApiResponse.success(result);
    }
}
