package com.hape.photogallery;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** Konami 码解锁 — 能走到这里说明已通过前端摇杆验证，直接签发 admin JWT */
    @PostMapping("/api/auth/unlock")
    public ApiResponse<Map<String, Object>> unlock() {
        String token = JwtUtil.issueAdmin(24 * 60 * 60 * 1000);
        log.info("Admin JWT issued");
        return ApiResponse.success(Map.of(
                "token", token,
                "expiresIn", 86400
        ));
    }

    /** 管理员生成分享链接 */
    @PostMapping("/api/share/generate")
    public ApiResponse<Map<String, String>> generateShare(@RequestBody Map<String, Object> body) {
        Object raw = body.get("photoIds");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(400, "请选择至少一张照片");
        }
        List<Long> photoIds = list.stream()
                .map(o -> ((Number) o).longValue())
                .toList();
        String permission = (String) body.getOrDefault("permission", "view");
        int expireDays = body.get("expireDays") != null
                ? ((Number) body.get("expireDays")).intValue() : 7;

        String token = JwtUtil.issueShare(photoIds, permission, expireDays * 24L * 60 * 60 * 1000);
        String shareUrl = "/share/" + token;

        return ApiResponse.success(Map.of(
                "url", shareUrl,
                "token", token,
                "expiresIn", String.valueOf(expireDays * 86400)
        ));
    }
}
