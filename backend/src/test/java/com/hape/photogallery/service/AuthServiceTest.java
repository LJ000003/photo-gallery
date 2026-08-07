package com.hape.photogallery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hape.photogallery.config.FailedAttemptStore;
import com.hape.photogallery.config.JwtService;
import com.hape.photogallery.config.NonceStore;
import com.hape.photogallery.entity.ShareToken;
import com.hape.photogallery.repository.ShareTokenRepository;
import com.hape.photogallery.service.AuthService.AuthResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 解锁校验逻辑（从 AuthController 抽入 AuthService 后）：
 * 封禁 403 / nonce 失效 400 / 序列错 401 / 不完整 400 / 成功签发 + reset；分享生成。
 * konami-sequence 经 @Value 注入，单测用 ReflectionTestUtils 设置。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SEQUENCE = "up,up,down,down,left,right,left,right,B,A,B,A";

    @Mock private JwtService jwtService;
    @Mock private NonceStore nonceStore;
    @Mock private FailedAttemptStore failedAttemptStore;
    @Mock private ShareTokenRepository shareTokenRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(jwtService, nonceStore, failedAttemptStore,
                shareTokenRepository, objectMapper);
        ReflectionTestUtils.setField(service, "konamiSequence", SEQUENCE);
    }

    private ShareToken activeToken(String token, String photoIds, String permission) {
        ShareToken st = new ShareToken();
        st.setToken(token);
        st.setPhotoIds(photoIds);
        st.setPermission(permission);
        st.setExpiresAt(LocalDateTime.now().plusDays(7));
        st.setCreatedAt(LocalDateTime.now());
        return st;
    }

    private Map<String, Object> unlockBody(String nonce, List<String> keys) {
        return Map.of("nonce", nonce, "keys", keys);
    }

    private List<String> correctKeys() {
        return List.of("up", "up", "down", "down", "left", "right", "left", "right", "B", "A", "B", "A");
    }

    @Test
    void unlock_blockedIp_shouldReturn403() {
        when(failedAttemptStore.isBlocked("1.2.3.4")).thenReturn(true);

        AuthResult result = service.unlock("1.2.3.4", unlockBody("x", List.of()));

        assertThat(result.status()).isEqualTo(403);
        assertThat(result.message()).contains("尝试次数过多");
        verify(nonceStore, never()).consume(anyString());
    }

    // ---- P1 回归：畸形输入 → 400 + 计数，不再 500 且绕过封禁计数 ----

    @Test
    void unlock_nullBody_shouldReturn400AndRecordFailure() {
        when(failedAttemptStore.isBlocked(anyString())).thenReturn(false);
        when(failedAttemptStore.remainingAttempts(anyString())).thenReturn(4);

        AuthResult result = service.unlock("1.2.3.4", null);

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.message()).contains("请求格式有误");
        verify(failedAttemptStore).recordFailure("1.2.3.4");
        verify(nonceStore, never()).consume(anyString());
    }

    @Test
    void unlock_nonceWrongType_shouldReturn400AndRecordFailure() {
        when(failedAttemptStore.isBlocked(anyString())).thenReturn(false);
        when(failedAttemptStore.remainingAttempts(anyString())).thenReturn(4);

        AuthResult result = service.unlock("1.2.3.4", Map.of("nonce", 123, "keys", correctKeys()));

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.message()).contains("请求格式有误");
        verify(failedAttemptStore).recordFailure("1.2.3.4");
        verify(nonceStore, never()).consume(anyString());
    }

    @Test
    void unlock_keysWrongType_shouldReturn400AndRecordFailure() {
        when(failedAttemptStore.isBlocked(anyString())).thenReturn(false);
        when(failedAttemptStore.remainingAttempts(anyString())).thenReturn(4);

        // keys 为 JSON 字符串而非数组——旧实现 (List<String>) 强转抛 ClassCastException → 500
        AuthResult result = service.unlock("1.2.3.4",
                Map.of("nonce", "valid-nonce", "keys", "up,up,down,down,left,right,left,right,B,A,B,A"));

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.message()).contains("请求格式有误");
        verify(failedAttemptStore).recordFailure("1.2.3.4");
        verify(nonceStore, never()).consume(anyString());
    }

    @Test
    void unlock_keysNonStringElements_shouldReturn400AndRecordFailure() {
        when(failedAttemptStore.isBlocked(anyString())).thenReturn(false);
        when(failedAttemptStore.remainingAttempts(anyString())).thenReturn(4);

        // 12 个数字元素——旧实现 size 校验通过后 String.join 抛 ClassCastException → 500
        List<Object> numericKeys = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        AuthResult result = service.unlock("1.2.3.4",
                Map.of("nonce", "valid-nonce", "keys", numericKeys));

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.message()).contains("请求格式有误");
        verify(failedAttemptStore).recordFailure("1.2.3.4");
        verify(nonceStore, never()).consume(anyString());
    }

    @Test
    void unlock_invalidNonce_shouldReturn400AndRecordFailure() {
        when(failedAttemptStore.isBlocked(anyString())).thenReturn(false);
        when(failedAttemptStore.remainingAttempts(anyString())).thenReturn(4);
        when(nonceStore.consume("stale-nonce")).thenReturn(false);

        AuthResult result = service.unlock("1.2.3.4", unlockBody("stale-nonce", List.of()));

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.message()).contains("请求无效或已过期");
        verify(failedAttemptStore).recordFailure("1.2.3.4");
    }

    @Test
    void unlock_incompleteKeys_shouldReturn400() {
        when(failedAttemptStore.isBlocked(anyString())).thenReturn(false);
        when(failedAttemptStore.remainingAttempts(anyString())).thenReturn(4);
        when(nonceStore.consume("valid-nonce")).thenReturn(true);

        AuthResult result = service.unlock("1.2.3.4", unlockBody("valid-nonce", List.of("up", "up")));

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.message()).contains("输入不完整");
    }

    @Test
    void unlock_wrongSequence_shouldReturn401AndRecordFailure() {
        when(failedAttemptStore.isBlocked(anyString())).thenReturn(false);
        when(failedAttemptStore.remainingAttempts(anyString())).thenReturn(4);
        when(nonceStore.consume("valid-nonce")).thenReturn(true);

        AuthResult result = service.unlock("1.2.3.4",
                unlockBody("valid-nonce", List.of("up", "up", "up", "up", "left", "right", "left", "right", "B", "A", "B", "A")));

        assertThat(result.status()).isEqualTo(401);
        assertThat(result.message()).contains("序列不正确");
        verify(failedAttemptStore).recordFailure("1.2.3.4");
        verify(jwtService, never()).issueAdmin(anyLong());
    }

    @Test
    void unlock_validKeys_shouldIssueAdminTokenAndReset() {
        when(failedAttemptStore.isBlocked(anyString())).thenReturn(false);
        when(nonceStore.consume("valid-nonce")).thenReturn(true);
        when(jwtService.issueAdmin(anyLong())).thenReturn("jwt-token");

        AuthResult result = service.unlock("1.2.3.4", unlockBody("valid-nonce", correctKeys()));

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.data()).containsEntry("token", "jwt-token");
        assertThat(result.data()).containsEntry("expiresIn", 86400);
        verify(failedAttemptStore).reset("1.2.3.4");
    }

    // ==================== 分享 token（DB token + 幂等复用） ====================

    @Test
    void generateShare_shouldPersistShareToken() {
        when(shareTokenRepository.findAllByRevokedAtIsNullAndExpiresAtAfter(any()))
                .thenReturn(List.of());

        AuthResult result = service.generateShare(List.of(2L, 1L, 2L), "view", 7);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.data()).containsKey("token");
        assertThat(result.data()).containsEntry("expiresIn", "604800");
        assertThat(result.data().get("url")).isEqualTo("/share/" + result.data().get("token"));

        ArgumentCaptor<ShareToken> captor = ArgumentCaptor.forClass(ShareToken.class);
        verify(shareTokenRepository).save(captor.capture());
        ShareToken saved = captor.getValue();
        assertThat(saved.getPhotoIds()).isEqualTo("[1,2]"); // 规范化：排序去重
        assertThat(saved.getPermission()).isEqualTo("view");
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));
        assertThat(saved.getToken()).matches("^[A-Za-z0-9_-]{40,50}$"); // base64url(32B)
    }

    @Test
    void generateShare_sameContent_shouldReuseExistingToken() {
        ShareToken existing = activeToken("existing-token", "[1,2]", "view");
        when(shareTokenRepository.findAllByRevokedAtIsNullAndExpiresAtAfter(any()))
                .thenReturn(List.of(existing));

        AuthResult result = service.generateShare(List.of(1L, 2L), "view", 7);

        assertThat(result.data()).containsEntry("url", "/share/existing-token");
        assertThat(result.data()).containsEntry("token", "existing-token");
        verify(shareTokenRepository, never()).save(any());
    }

    @Test
    void generateShare_sameContentDifferentPermission_shouldCreateNew() {
        ShareToken existing = activeToken("existing-token", "[1,2]", "download");
        when(shareTokenRepository.findAllByRevokedAtIsNullAndExpiresAtAfter(any()))
                .thenReturn(List.of(existing));

        AuthResult result = service.generateShare(List.of(1L, 2L), "view", 7);

        assertThat(result.data()).doesNotContainEntry("token", "existing-token");
        verify(shareTokenRepository).save(any(ShareToken.class));
    }

    @Test
    void generateShare_nullPermission_shouldThrow400() {
        // @Pattern 不拦 null——service 层防御：null 权限曾 NPE → 500 / null 落库
        assertThatThrownBy(() -> service.generateShare(List.of(1L), null, 7))
                .isInstanceOf(com.hape.photogallery.exception.BusinessException.class)
                .hasMessageContaining("分享权限不能为空");
        verify(shareTokenRepository, never()).save(any());
    }

    @Test
    void revokeShare_notFound_should404() {
        when(shareTokenRepository.findByToken("no-such")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.revokeShare("no-such"))
                .isInstanceOf(com.hape.photogallery.exception.BusinessException.class)
                .hasMessageContaining("分享链接不存在");
    }

    @Test
    void revokeShare_active_shouldSetRevokedAt() {
        ShareToken st = activeToken("tok-1", "[1]", "view");
        when(shareTokenRepository.findByToken("tok-1")).thenReturn(java.util.Optional.of(st));

        service.revokeShare("tok-1");

        assertThat(st.getRevokedAt()).isNotNull();
        verify(shareTokenRepository).save(st);
    }

    @Test
    void revokeShare_alreadyRevoked_shouldBeIdempotent() {
        ShareToken st = activeToken("tok-1", "[1]", "view");
        st.setRevokedAt(LocalDateTime.now().minusHours(1));
        when(shareTokenRepository.findByToken("tok-1")).thenReturn(java.util.Optional.of(st));

        service.revokeShare("tok-1");

        verify(shareTokenRepository, never()).save(any());
    }
}
