package com.hape.photogallery.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.hape.photogallery.repository.ShareTokenRepository;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.hape.photogallery.config.ClientIpResolver;
import com.hape.photogallery.config.JwtService;
import com.hape.photogallery.config.MediaSignatureService;
import com.hape.photogallery.service.AuthService;
import com.hape.photogallery.service.AuthService.AuthResult;

/**
 * AuthController HTTP 层测试（抽 AuthService 后：校验逻辑移至 AuthServiceTest，
 * 此处只测 MVC 胶水：状态码/响应体转发；IP 解析留 controller）。
 * 三件套 @Import：@WebMvcTest 会加载 Filter 类型的 JwtAuthFilter，其构造依赖
 * JwtService + MediaSignatureService（现有 Controller 测试模板同源）。
 */
@WebMvcTest(value = AuthController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import({ClientIpResolver.class, JwtService.class, MediaSignatureService.class})
class AuthControllerTest {
    @MockBean private ShareTokenRepository shareTokenRepository;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void challenge_shouldReturnNonce() throws Exception {
        when(authService.generateNonce()).thenReturn("nonce-123");

        mockMvc.perform(get("/api/v1/auth/challenge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nonce").value("nonce-123"));
    }

    @Test
    void unlock_validKeys_shouldIssueAdminToken() throws Exception {
        when(authService.unlock(anyString(), any()))
                .thenReturn(new AuthResult(200, null, Map.of("token", "jwt-token", "expiresIn", 86400)));

        mockMvc.perform(post("/api/v1/auth/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nonce\":\"valid-nonce\",\"keys\":["
                                + "\"up\",\"up\",\"down\",\"down\",\"left\",\"right\",\"left\",\"right\",\"B\",\"A\",\"B\",\"A\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(86400));

        verify(authService).unlock(anyString(), any());
    }

    @Test
    void unlock_wrongSequence_shouldReturn401() throws Exception {
        when(authService.unlock(anyString(), any()))
                .thenReturn(new AuthResult(401, "序列不正确（剩余 4 次）", null));

        mockMvc.perform(post("/api/v1/auth/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nonce\":\"valid-nonce\",\"keys\":["
                                + "\"up\",\"up\",\"up\",\"up\",\"left\",\"right\",\"left\",\"right\",\"B\",\"A\",\"B\",\"A\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("序列不正确")));
    }

    @Test
    void unlock_blockedIp_shouldReturn403() throws Exception {
        when(authService.unlock(anyString(), any()))
                .thenReturn(new AuthResult(403, "尝试次数过多，请 15 分钟后再试", null));

        mockMvc.perform(post("/api/v1/auth/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nonce\":\"x\",\"keys\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("尝试次数过多")));
    }

    @Test
    void unlock_invalidNonce_shouldReturn400() throws Exception {
        when(authService.unlock(anyString(), any()))
                .thenReturn(new AuthResult(400, "请求无效或已过期，请重试（剩余 4 次）", null));

        mockMvc.perform(post("/api/v1/auth/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nonce\":\"stale-nonce\",\"keys\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("请求无效或已过期")));
    }

    @Test
    void unlock_incompleteKeys_shouldReturn400() throws Exception {
        when(authService.unlock(anyString(), any()))
                .thenReturn(new AuthResult(400, "输入不完整（剩余 4 次）", null));

        mockMvc.perform(post("/api/v1/auth/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nonce\":\"valid-nonce\",\"keys\":[\"up\",\"up\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("输入不完整")));
    }

    @Test
    void generateShare_shouldIssueViewerToken() throws Exception {
        when(authService.generateShare(any(), anyString(), anyInt()))
                .thenReturn(new AuthResult(200, null, Map.of(
                        "url", "/share/viewer-token", "token", "viewer-token", "expiresIn", "604800")));

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

    @Test
    void revokeShare_shouldCallService() throws Exception {
        mockMvc.perform(post("/api/v1/share/some-token/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("分享链接已撤销"));

        verify(authService).revokeShare("some-token");
    }
}
