package com.hape.photogallery.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 图片短时签名（P1-11）：sign/verify 往返、篡改/非法输入拒绝、
 * 滑动时间桶（前一桶仍有效、跨两桶过期）。
 */
class MediaSignatureServiceTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

    private MediaSignatureService service(long windowSeconds) {
        return new MediaSignatureService(SECRET, windowSeconds);
    }

    @Test
    void sign_verify_roundTrip() {
        MediaSignatureService s = service(300);
        long photoId = s.verify(s.sign(42));
        assertThat(photoId).isEqualTo(42);
    }

    @Test
    void verify_distinctPhotoId_rejected() {
        MediaSignatureService s = service(300);
        String token = s.sign(42);
        assertThat(s.verify(token)).isEqualTo(42);
        // 同一 token 不可能伪造出另一个 photoId（HMAC 绑定），篡改任一字段即失败
    }

    @Test
    void tampered_hmac_rejected() {
        MediaSignatureService s = service(300);
        String token = s.sign(42);
        String tampered = token.substring(0, token.length() - 2) + "AA";
        assertThat(s.verify(tampered)).isEqualTo(-1);
    }

    @Test
    void invalid_inputs_rejected() {
        MediaSignatureService s = service(300);
        assertThat(s.verify("")).isEqualTo(-1);
        assertThat(s.verify("not-base64!!!")).isEqualTo(-1);
        assertThat(s.verify("a.b.c.d")).isEqualTo(-1);
        assertThat(s.verify("1.abc.xyz")).isEqualTo(-1);
    }

    @Test
    void oneWindowLater_stillValid() throws InterruptedException {
        // 1 秒桶：0.55s 最多跨 1 个桶边界，签名应仍有效（滑动窗口容忍前一桶，缓存友好）
        MediaSignatureService s = service(1);
        String token = s.sign(7);
        Thread.sleep(550);
        assertThat(s.verify(token)).isEqualTo(7);
    }

    @Test
    void older_than_twoBuckets_expired() throws InterruptedException {
        // 1 秒桶：2.6s 必然跨 ≥2 个桶边界 → 签名过期
        MediaSignatureService s = service(1);
        String token = s.sign(7);
        Thread.sleep(2600);
        assertThat(s.verify(token)).isEqualTo(-1);
    }

    @Test
    void blankSecret_shouldThrow() {
        try {
            new MediaSignatureService("", 300);
            org.assertj.core.api.Assertions.fail("应为空密钥抛 IllegalStateException");
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage()).contains("JWT_SECRET");
        }
    }
}
