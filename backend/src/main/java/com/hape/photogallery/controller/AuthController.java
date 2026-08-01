package com.hape.photogallery.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hape.photogallery.ApiResponse;
import com.hape.photogallery.config.FailedAttemptStore;
import com.hape.photogallery.config.JwtService;
import com.hape.photogallery.config.NonceStore;
import com.hape.photogallery.dto.ShareGenerateRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtService jwtService;
    private final NonceStore nonceStore;
    private final FailedAttemptStore failedAttemptStore;

    @Value("${auth.konami-sequence}")
    private String konamiSequence;

    public AuthController(JwtService jwtService, NonceStore nonceStore,
                          FailedAttemptStore failedAttemptStore) {
        this.jwtService = jwtService;
        this.nonceStore = nonceStore;
        this.failedAttemptStore = failedAttemptStore;
    }

    /** 获取一次性 nonce，60 秒有效 */
    @GetMapping("/api/v1/auth/challenge")
    public ApiResponse<Map<String, String>> challenge() {
        String nonce = nonceStore.generate();
        return ApiResponse.success(Map.of("nonce", nonce));
    }

    /** Konami 解锁 —— 前端传来 nonce + 按键序列，后端验证 */
    @SuppressWarnings("unchecked")
    @PostMapping("/api/v1/auth/unlock")
    public ResponseEntity<ApiResponse<Map<String, Object>>> unlock(@RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        String ip = resolveIp(request);

        // 封禁检查
        if (failedAttemptStore.isBlocked(ip)) {
            return ResponseEntity.status(403).body(ApiResponse.error(403, "尝试次数过多，请 15 分钟后再试"));
        }

        // nonce 校验（一次性消费）
        String nonce = (String) body.get("nonce");
        if (nonce == null || !nonceStore.consume(nonce)) {
            failedAttemptStore.recordFailure(ip);
            int remaining = failedAttemptStore.remainingAttempts(ip);
            log.warn("Invalid/expired nonce from IP: {}, remaining attempts: {}", ip, remaining);
            return ResponseEntity.status(400).body(ApiResponse.error(400, "请求无效或已过期，请重试（剩余 " + remaining + " 次）"));
        }

        // 按键序列校验
        List<String> keys = (List<String>) body.get("keys");
        if (keys == null || keys.size() != 12) {
            failedAttemptStore.recordFailure(ip);
            int remaining = failedAttemptStore.remainingAttempts(ip);
            return ResponseEntity.status(400).body(ApiResponse.error(400, "输入不完整（剩余 " + remaining + " 次）"));
        }

        String actual = String.join(",", keys);
        if (!konamiSequence.equals(actual)) {
            failedAttemptStore.recordFailure(ip);
            int remaining = failedAttemptStore.remainingAttempts(ip);
            log.warn("Wrong Konami sequence from IP: {}, remaining: {}", ip, remaining);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "序列不正确（剩余 " + remaining + " 次）"));
        }

        // 验证通过
        failedAttemptStore.reset(ip);
        String token = jwtService.issueAdmin(24 * 60 * 60 * 1000);
        log.info("Admin JWT issued for IP: {}", ip);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "token", token,
                "expiresIn", 86400
        )));
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

    private String resolveIp(HttpServletRequest request) {
        // 优先 X-Real-IP（反向代理设置，客户端不可伪造），X-Forwarded-For 仅作 fallback
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
