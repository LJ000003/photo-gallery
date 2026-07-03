package com.hape.photogallery;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {

    private final PasswordEncoder passwordEncoder;
    private final String passwordHash;

    public AuthController(PasswordEncoder passwordEncoder,
                          @Value("${admin.password}") String adminPassword) {
        this.passwordEncoder = passwordEncoder;
        this.passwordHash = passwordEncoder.encode(adminPassword);
    }

    /** Konami 解锁后调用，拿 admin JWT */
    @PostMapping("/api/auth/unlock")
    public ApiResponse<Map<String, Object>> unlock(@RequestBody Map<String, String> body) {
        String password = body.getOrDefault("password", "");
        if (!passwordEncoder.matches(password, passwordHash)) {
            throw new RuntimeException("密码错误");
        }
        String token = JwtUtil.issueAdmin(24 * 60 * 60 * 1000); // 24h
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
            throw new RuntimeException("请选择至少一张照片");
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
