package com.hape.photogallery.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.hape.photogallery.config.ClientIpResolver;
import com.hape.photogallery.config.FailedAttemptStore;
import com.hape.photogallery.config.JwtService;
import com.hape.photogallery.config.MediaSignatureService;
import com.hape.photogallery.config.NonceStore;

/**
 * AuthController 安全边界测试：解锁成功 / 序列错 401 / 封禁 403 / nonce 失效 400 / challenge / 分享生成。
 * konami-sequence 从主 application.properties 读取（测试类路径可见）。
 * 三件套 @Import：@WebMvcTest 会加载 Filter 类型的 JwtAuthFilter，其构造依赖
 * JwtService + MediaSignatureService（现有 Controller 测试模板同源）。
 */
@WebMvcTest(value = AuthController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import({ClientIpResolver.class, JwtService.class, MediaSignatureService.class})
class AuthControllerTest {

    private static final List<String> CORRECT_KEYS = List.of(
            "up", "up", "down", "down", "left", "right", "left", "right", "B", "A", "B", "A");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NonceStore nonceStore;

    @MockBean
    private FailedAttemptStore failedAttemptStore;

    @MockBean
    private JwtService jwtService;

    @Test
    void challenge_shouldReturnNonce() throws Exception {
        when(nonceStore.generate()).thenReturn("nonce-123");

        mockMvc.perform(get("/api/v1/auth/challenge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nonce").value("nonce-123"));
    }

    @Test
    void unlock_validKeys_shouldIssueAdminToken() throws Exception {
        when(failedAttemptStore.isBlocked(anyString())).thenReturn(false);
        when(nonceStore.consume("valid-nonce")).thenReturn(true);
        when(jwtService.issueAdmin(anyLong())).thenReturn("jwt-token");

        mockMvc.perform(post("/api/v1/auth/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nonce\":\"valid-nonce\",\"keys\":"
                                + "[\"up\",\"up\",\"down\",\"down\",\"left\",\"right\",\"left\",\"right\",\"B\",\"A\",\"B\",\"A\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(86400));

        verify(failedAttemptStore).reset(anyString());
    }

    @Test
    void unlock_wrongSequence_shouldReturn401() throws Exception {
        when(failedAttemptStore.isBlocked(anyString())).thenReturn(false);
        when(failedAttemptStore.remainingAttempts(anyString())).thenReturn(4);
        when(nonceStore.consume("valid-nonce")).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nonce\":\"valid-nonce\",\"keys\":"
                                + "[\"up\",\"up\",\"up\",\"up\",\"left\",\"right\",\"left\",\"right\",\"B\",\"A\",\"B\",\"A\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("序列不正确")));

        verify(failedAttemptStore).recordFailure(anyString());
        verify(jwtService, never()).issueAdmin(anyLong());
    }

    @Test
    void unlock_blockedIp_shouldReturn403() throws Exception {
        when(failedAttemptStore.isBlocked(anyString())).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nonce\":\"x\",\"keys\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("尝试次数过多")));

        verify(nonceStore, never()).consume(anyString());
    }

    @Test
    void unlock_invalidNonce_shouldReturn400() throws Exception {
        when(failedAttemptStore.isBlocked(anyString())).thenReturn(false);
        when(failedAttemptStore.remainingAttempts(anyString())).thenReturn(4);
        when(nonceStore.consume("stale-nonce")).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nonce\":\"stale-nonce\",\"keys\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("请求无效或已过期")));

        verify(failedAttemptStore).recordFailure(anyString());
    }

    @Test
    void unlock_incompleteKeys_shouldReturn400() throws Exception {
        when(failedAttemptStore.isBlocked(anyString())).thenReturn(false);
        when(failedAttemptStore.remainingAttempts(anyString())).thenReturn(4);
        when(nonceStore.consume("valid-nonce")).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nonce\":\"valid-nonce\",\"keys\":[\"up\",\"up\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("输入不完整")));
    }

    @Test
    void generateShare_shouldIssueViewerToken() throws Exception {
        when(jwtService.issueShare(any(), anyString(), anyLong())).thenReturn("viewer-token");

        mockMvc.perform(post("/api/v1/share/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoIds\":[1,2],\"permission\":\"view\",\"expireDays\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("/share/viewer-token"))
                .andExpect(jsonPath("$.data.token").value("viewer-token"));
    }

    @Test
    void generateShare_emptyPhotoIds_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/share/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoIds\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
