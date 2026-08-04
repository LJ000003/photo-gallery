package com.hape.photogallery.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hape.photogallery.config.FailedAttemptStore;
import com.hape.photogallery.config.JwtService;
import com.hape.photogallery.config.NonceStore;

/**
 * 认证逻辑（P4-#48④：从 AuthController 抽出）。
 * 请求上下文（IP 解析）留在 MVC 层，IP 作为参数传入——封禁/计数不依赖 servlet API，可单测。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final JwtService jwtService;
    private final NonceStore nonceStore;
    private final FailedAttemptStore failedAttemptStore;

    @Value("${auth.konami-sequence}")
    private String konamiSequence;

    public AuthService(JwtService jwtService, NonceStore nonceStore,
                       FailedAttemptStore failedAttemptStore) {
        this.jwtService = jwtService;
        this.nonceStore = nonceStore;
        this.failedAttemptStore = failedAttemptStore;
    }

    /** 解锁结果：status=200 时 data 为 {token, expiresIn}，否则 message 为错误文案 */
    public record AuthResult(int status, String message, Map<String, Object> data) {}

    public String generateNonce() {
        return nonceStore.generate();
    }

    /** Konami 解锁校验（Challenge-Response 第二步） */
    public AuthResult unlock(String ip, Map<String, Object> body) {
        // 封禁检查
        if (failedAttemptStore.isBlocked(ip)) {
            return new AuthResult(403, "尝试次数过多，请 15 分钟后再试", null);
        }

        // nonce 校验（一次性消费）
        String nonce = (String) body.get("nonce");
        if (nonce == null || !nonceStore.consume(nonce)) {
            failedAttemptStore.recordFailure(ip);
            int remaining = failedAttemptStore.remainingAttempts(ip);
            log.warn("Invalid/expired nonce from IP: {}, remaining attempts: {}", ip, remaining);
            return new AuthResult(400, "请求无效或已过期，请重试（剩余 " + remaining + " 次）", null);
        }

        // 按键序列校验
        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) body.get("keys");
        if (keys == null || keys.size() != 12) {
            failedAttemptStore.recordFailure(ip);
            int remaining = failedAttemptStore.remainingAttempts(ip);
            return new AuthResult(400, "输入不完整（剩余 " + remaining + " 次）", null);
        }

        String actual = String.join(",", keys);
        if (!konamiSequence.equals(actual)) {
            failedAttemptStore.recordFailure(ip);
            int remaining = failedAttemptStore.remainingAttempts(ip);
            log.warn("Wrong Konami sequence from IP: {}, remaining: {}", ip, remaining);
            return new AuthResult(401, "序列不正确（剩余 " + remaining + " 次）", null);
        }

        // 验证通过
        failedAttemptStore.reset(ip);
        String token = jwtService.issueAdmin(24 * 60 * 60 * 1000);
        log.info("Admin JWT issued for IP: {}", ip);
        return new AuthResult(200, null, Map.of("token", token, "expiresIn", 86400));
    }

    /** 生成分享链接（7 天 viewer JWT） */
    public AuthResult generateShare(List<Long> photoIds, String permission, int expireDays) {
        String token = jwtService.issueShare(photoIds, permission,
                expireDays * 24L * 60 * 60 * 1000);
        return new AuthResult(200, null, Map.of(
                "url", "/share/" + token,
                "token", token,
                "expiresIn", String.valueOf(expireDays * 86400)));
    }
}
