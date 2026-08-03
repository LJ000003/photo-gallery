package com.hape.photogallery.config;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 分享权限强制执行测试（P0-4）：view 权限禁止下载原图（/file），
 * download 权限与缩略图/WebP 端点不受影响；photoId 白名单越界仍 403。
 * 图片短时签名（P1-11）：有效签名直接放行（不携带会话凭证），
 * 绑定 photoId 不符/篡改签名一律 403，非图片端点忽略签名参数。
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private Claims claims;

    private JwtAuthFilter filter;
    private MediaSignatureService sigService;
    private MockHttpServletResponse response;
    private boolean chainCalled;

    @BeforeEach
    void setUp() {
        sigService = new MediaSignatureService("test-secret-0123456789abcdef0123456789abcdef", 300);
        filter = new JwtAuthFilter(jwtService, sigService);
        chainCalled = false;
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse apply(String uri, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (token != null) request.addParameter("token", token);
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> chainCalled = true);
        return response;
    }

    private void stubViewer(String permission, List<Long> photoIds) {
        when(jwtService.verify("tok")).thenReturn(claims);
        when(claims.get("role", String.class)).thenReturn("viewer");
        when(claims.get("photos", List.class)).thenReturn(photoIds);
        when(claims.get("permission", String.class)).thenReturn(permission);
    }

    @Test
    void viewer_viewPermission_file_shouldBe403() throws Exception {
        stubViewer("view", List.of(1L));
        MockHttpServletResponse res = apply("/api/v1/photos/1/file", "tok");
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void viewer_downloadPermission_file_shouldPass() throws Exception {
        stubViewer("download", List.of(1L));
        apply("/api/v1/photos/1/file", "tok");
        assertThat(response.getStatus()).isNotEqualTo(403);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void viewer_missingPermission_file_shouldBe403() throws Exception {
        stubViewer(null, List.of(1L));
        MockHttpServletResponse res = apply("/api/v1/photos/1/file", "tok");
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void viewer_viewPermission_webp_shouldPass() throws Exception {
        stubViewer("view", List.of(1L));
        apply("/api/v1/photos/1/webp", "tok");
        assertThat(response.getStatus()).isNotEqualTo(403);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void viewer_viewPermission_thumbnail_shouldPass() throws Exception {
        stubViewer("view", List.of(1L));
        apply("/api/v1/photos/1/thumbnail?w=400", "tok");
        assertThat(chainCalled).isTrue();
    }

    @Test
    void viewer_photoOutsideWhitelist_file_shouldBe403() throws Exception {
        stubViewer("download", List.of(2L));
        MockHttpServletResponse res = apply("/api/v1/photos/1/file", "tok");
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void admin_file_shouldPass() throws Exception {
        when(jwtService.verify("tok")).thenReturn(claims);
        when(claims.get("role", String.class)).thenReturn("admin");
        apply("/api/v1/photos/1/file", "tok");
        assertThat(response.getStatus()).isNotEqualTo(403);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void noToken_shouldPassThrough() throws Exception {
        apply("/api/v1/photos/1/file", null);
        assertThat(chainCalled).isTrue();
    }

    /* ---------- 图片短时签名（P1-11） ---------- */

    private MockHttpServletResponse applySig(String uri, String sig) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (sig != null) request.addParameter("sig", sig);
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> chainCalled = true);
        return response;
    }

    @Test
    void validSig_thumbnail_shouldPass() throws Exception {
        MockHttpServletResponse res = applySig("/api/v1/photos/42/thumbnail", sigService.sign(42));
        assertThat(res.getStatus()).isNotEqualTo(403);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void validSig_file_shouldPass() throws Exception {
        MockHttpServletResponse res = applySig("/api/v1/photos/42/file", sigService.sign(42));
        assertThat(res.getStatus()).isNotEqualTo(403);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void sig_forDifferentPhotoId_shouldBe403() throws Exception {
        MockHttpServletResponse res = applySig("/api/v1/photos/41/file", sigService.sign(42));
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void tamperedSig_shouldBe403() throws Exception {
        MockHttpServletResponse res = applySig("/api/v1/photos/42/file", sigService.sign(42) + "x");
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void garbageSig_shouldBe403() throws Exception {
        MockHttpServletResponse res = applySig("/api/v1/photos/42/file", "not-a-signature");
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void sig_onNonImagePath_shouldBeIgnored() throws Exception {
        // 非图片端点不验签：无 Authorization 头时签名参数不影响 JWT 逻辑（透传，最终由 Security 规则拦截）
        MockHttpServletResponse res = applySig("/api/v1/photos?page=0", sigService.sign(42));
        assertThat(chainCalled).isTrue();
    }
}
