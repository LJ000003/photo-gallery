package com.hape.photogallery.controller;

import java.util.List;
import java.util.Map;

import com.hape.photogallery.ApiResponse;
import com.hape.photogallery.config.JwtService;
import com.hape.photogallery.dto.ShareGenerateRequest;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /** Konami 码解锁 — 能走到这里说明已通过前端摇杆验证，直接签发 admin JWT */
    @PostMapping("/api/v1/auth/unlock")
    public ApiResponse<Map<String, Object>> unlock() {
        String token = jwtService.issueAdmin(24 * 60 * 60 * 1000);
        log.info("Admin JWT issued");
        return ApiResponse.success(Map.of(
                "token", token,
                "expiresIn", 86400
        ));
    }

    /** 管理员生成分享链接 */
    @PostMapping("/api/v1/share/generate")
    public ApiResponse<Map<String, String>> generateShare(@Valid @RequestBody ShareGenerateRequest req) {
        String token = jwtService.issueShare(req.getPhotoIds(), req.getPermission(),
                req.getExpireDays() * 24L * 60 * 60 * 1000);
        String shareUrl = "/share/" + token;

        return ApiResponse.success(Map.of(
                "url", shareUrl,
                "token", token,
                "expiresIn", String.valueOf(req.getExpireDays() * 86400)
        ));
    }
}
