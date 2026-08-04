package com.hape.photogallery.service;

import com.hape.photogallery.config.FailedAttemptStore;
import com.hape.photogallery.config.JwtService;
import com.hape.photogallery.config.NonceStore;
import com.hape.photogallery.service.AuthService.AuthResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 解锁校验逻辑（P4-#48④ 从 AuthController 抽入 AuthService 后）：
 * 封禁 403 / nonce 失效 400 / 序列错 401 / 不完整 400 / 成功签发 + reset；分享生成。
 * konami-sequence 经 @Value 注入，单测用 ReflectionTestUtils 设置。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SEQUENCE = "up,up,down,down,left,right,left,right,B,A,B,A";

    @Mock private JwtService jwtService;
    @Mock private NonceStore nonceStore;
    @Mock private FailedAttemptStore failedAttemptStore;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(jwtService, nonceStore, failedAttemptStore);
        ReflectionTestUtils.setField(service, "konamiSequence", SEQUENCE);
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

    @Test
    void generateShare_shouldIssueViewerToken() {
        when(jwtService.issueShare(any(), anyString(), anyLong())).thenReturn("viewer-token");

        AuthResult result = service.generateShare(List.of(1L, 2L), "view", 7);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.data()).containsEntry("url", "/share/viewer-token");
        assertThat(result.data()).containsEntry("token", "viewer-token");
    }
}
