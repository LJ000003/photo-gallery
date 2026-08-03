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
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private Claims claims;

    private JwtAuthFilter filter;
    private MockHttpServletResponse response;
    private boolean chainCalled;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtService);
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
}
