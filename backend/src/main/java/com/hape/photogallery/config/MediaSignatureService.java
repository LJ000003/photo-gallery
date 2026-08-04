package com.hape.photogallery.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 图片访问短时签名（HMAC-SHA256 时间桶）：
 * token = base64url("{photoId}.{bucket}.{b64hmac}")，bucket = epochSeconds / windowSeconds。
 *
 * 设计要点：
 * - <img> 无法带 Authorization 头，旧方案把 24h 管理员 JWT 放进 query string，
 *   随访问日志/浏览器历史/Referer 泄漏。本服务改发「只绑定单张照片、无任何
 *   会话权限」的短时签名，URL 不再出现 JWT。
 * - 时间桶而非绝对过期：校验接受当前与前一个桶（滑动窗口 ~2×300s），
 *   列表响应被 Caffeine/Redis 缓存（{@code @Cacheable}）后签名跨桶依然有效，
 *   无需随响应刷新。
 * - 密钥从 JWT_SECRET 派生（SHA-256 单向扩展），不直接复用签名密钥。
 */
@Component
public class MediaSignatureService {

    private final byte[] key;
    private final long windowSeconds;

    public MediaSignatureService(@Value("${JWT_SECRET:}") String jwtSecret,
                                 @Value("${media.signature-window-seconds:300}") long windowSeconds) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("必须设置环境变量 JWT_SECRET");
        }
        try {
            this.key = MessageDigest.getInstance("SHA-256")
                    .digest((jwtSecret + ":media-signature").getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        this.windowSeconds = windowSeconds;
    }

    public String sign(long photoId) {
        long bucket = currentBucket();
        return encode(photoId, bucket, hmac(photoId, bucket));
    }

    /** @return 校验通过返回签名绑定的 photoId，否则 -1 */
    public long verify(String token) {
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return -1;
        }
        String[] parts = decoded.split("\\.");
        if (parts.length != 3) return -1;
        long photoId;
        long bucket;
        String hmac;
        try {
            photoId = Long.parseLong(parts[0]);
            bucket = Long.parseLong(parts[1]);
            hmac = parts[2];
        } catch (NumberFormatException e) {
            return -1;
        }
        // 仅接受当前与前一桶（滑动窗口），跨桶缓存的签名仍可用
        if (Math.abs(bucket - currentBucket()) > 1) return -1;
        if (!MessageDigest.isEqual(
                hmac.getBytes(StandardCharsets.US_ASCII),
                hmac(photoId, bucket).getBytes(StandardCharsets.US_ASCII))) {
            return -1;
        }
        return photoId;
    }

    private long currentBucket() {
        return System.currentTimeMillis() / 1000 / windowSeconds;
    }

    private String hmac(long photoId, long bucket) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] out = mac.doFinal((photoId + "." + bucket).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private String encode(long photoId, long bucket, String hmac) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((photoId + "." + bucket + "." + hmac).getBytes(StandardCharsets.UTF_8));
    }
}
