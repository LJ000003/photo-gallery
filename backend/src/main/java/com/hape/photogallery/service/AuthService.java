package com.hape.photogallery.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hape.photogallery.config.FailedAttemptStore;
import com.hape.photogallery.config.JwtService;
import com.hape.photogallery.config.NonceStore;
import com.hape.photogallery.entity.ShareToken;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.ShareTokenRepository;

/**
 * 认证逻辑（从 AuthController 抽出）。
 * 请求上下文（IP 解析）留在 MVC 层，IP 作为参数传入——封禁/计数不依赖 servlet API，可单测。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final JwtService jwtService;
    private final NonceStore nonceStore;
    private final FailedAttemptStore failedAttemptStore;
    private final ShareTokenRepository shareTokenRepository;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${auth.konami-sequence}")
    private String konamiSequence;

    public AuthService(JwtService jwtService, NonceStore nonceStore,
                       FailedAttemptStore failedAttemptStore,
                       ShareTokenRepository shareTokenRepository, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.nonceStore = nonceStore;
        this.failedAttemptStore = failedAttemptStore;
        this.shareTokenRepository = shareTokenRepository;
        this.objectMapper = objectMapper;
    }

    /** 解锁结果：status=200 时 data 为 {token, expiresIn}，否则 message 为错误文案 */
    public record AuthResult(int status, String message, Map<String, Object> data) {}

    public String generateNonce() {
        return nonceStore.generate();
    }

    /** Konami 解锁校验（Challenge-Response 第二步） */
    public AuthResult unlock(String ip, Object body) {
        // 封禁检查
        if (failedAttemptStore.isBlocked(ip)) {
            return new AuthResult(403, "尝试次数过多，请 15 分钟后再试", null);
        }

        // 类型校验先行：无检查强转（nonce 非字符串 / keys 非数组 / 元素非字符串）
        // 曾抛 ClassCastException → 500，且异常发生在 recordFailure 之前——
        // 畸形输入既不触发封禁计数还可无限刷 500，契约要求 400 + 计数。
        // 参数为 Object（controller 不再预绑定 Map）：JSON 数组/字符串/字面量 null
        // 也走到这里统一记失败，不留「绑定阶段 400 不计数」的旁路。
        // 只校验类型不校验数量：nonce 失效语义仍优先于「输入不完整」（与拆出前一致）
        if (!(body instanceof Map<?, ?> map) || !(map.get("nonce") instanceof String nonce)) {
            return recordFailure(ip, "请求格式有误");
        }
        if (!(map.get("keys") instanceof List<?> rawKeys)
                || rawKeys.stream().anyMatch(k -> !(k instanceof String))) {
            return recordFailure(ip, "请求格式有误");
        }
        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) rawKeys;

        // nonce 校验（一次性消费）
        if (!nonceStore.consume(nonce)) {
            failedAttemptStore.recordFailure(ip);
            int remaining = failedAttemptStore.remainingAttempts(ip);
            log.warn("Invalid/expired nonce from IP: {}, remaining attempts: {}", ip, remaining);
            return new AuthResult(400, "请求无效或已过期，请重试（剩余 " + remaining + " 次）", null);
        }

        // 按键数量校验（nonce 已消费——与旧行为一致：输入不完整也消耗 nonce）
        if (keys.size() != 12) {
            return recordFailure(ip, "输入不完整");
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

    /** 畸形输入统一记账并返回 400（与 nonce/序列错误同一计数体系，防绕过封禁） */
    private AuthResult recordFailure(String ip, String message) {
        failedAttemptStore.recordFailure(ip);
        int remaining = failedAttemptStore.remainingAttempts(ip);
        log.warn("Malformed unlock request from IP: {}, remaining attempts: {}", ip, remaining);
        return new AuthResult(400, message + "（剩余 " + remaining + " 次）", null);
    }

    /**
     * 生成分享链接（DB token + 幂等复用，替代 7 天 viewer JWT——JWT 退出分享签发）。
     * 同 photoIds+permission 的未撤销未过期 token 存在时返回现有 URL（弹窗里的「撤销」即撤销该链接，
     * 之前发出的旧链接=同一链接，天然一致）；否则生成高熵随机 token 落库。
     * photoIds 规范化（排序去重）后序列化，保证集合比较稳定。
     * 并发双写：同 photoIds 并发两次可能各插一条（无唯一约束兜底），单管理员场景可接受。
     */
    public AuthResult generateShare(List<Long> photoIds, String permission, int expireDays) {
        // 防御：@Pattern 不拦 null（显式 "permission": null 曾 NPE → 500 / null 权限落库），
        // 绕过 controller 直调时同样 400
        if (permission == null) {
            throw new BusinessException(400, "分享权限不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        String normalized = normalizePhotoIds(photoIds);
        Optional<ShareToken> existing = shareTokenRepository
                .findAllByRevokedAtIsNullAndExpiresAtAfter(now).stream()
                .filter(st -> permission.equals(st.getPermission())
                        && normalized.equals(st.getPhotoIds()))
                .findFirst();
        if (existing.isPresent()) {
            ShareToken st = existing.get();
            // 复用存量 token：expiresIn 返回剩余秒数（而非请求的 7 天），前端展示与实际一致
            long remainingSec = Math.max(1, Duration.between(now, st.getExpiresAt()).getSeconds());
            return new AuthResult(200, null, Map.of(
                    "url", "/share/" + st.getToken(),
                    "token", st.getToken(),
                    "expiresIn", String.valueOf(remainingSec)));
        }

        ShareToken st = new ShareToken();
        st.setToken(generateToken());
        st.setPhotoIds(normalized);
        st.setPermission(permission);
        st.setExpiresAt(now.plusDays(expireDays));
        st.setCreatedAt(now);
        shareTokenRepository.save(st);
        return new AuthResult(200, null, Map.of(
                "url", "/share/" + st.getToken(),
                "token", st.getToken(),
                "expiresIn", String.valueOf(expireDays * 86400)));
    }

    /** 撤销分享链接（不存在 404；已撤销幂等——多端并发撤销不报错） */
    public void revokeShare(String token) {
        ShareToken st = shareTokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(404, "分享链接不存在"));
        if (st.getRevokedAt() == null) {
            st.setRevokedAt(LocalDateTime.now());
            shareTokenRepository.save(st);
        }
    }

    /** photoIds 排序去重后序列化 JSON（如 "[1,2,3]"），作为集合相等性比较的规范化键 */
    private String normalizePhotoIds(List<Long> photoIds) {
        try {
            return objectMapper.writeValueAsString(
                    photoIds.stream().distinct().sorted().toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("photoIds 序列化失败", e);
        }
    }

    /** 高熵随机 token（32 字节 → base64url ≈ 43 字符，URL 安全，碰撞概率可忽略） */
    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
