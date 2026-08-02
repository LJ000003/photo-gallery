package com.hape.photogallery.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证端点限流测试：unlock(POST)/challenge(GET) 共享 10 req/s 固定窗口，
 * 非认证端点与非 POST unlock 请求不受限。
 */
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    private MockHttpServletResponse apply(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path); // Tomcat 下 getServletPath() 即去掉 context 后的路径
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
        });
        return response;
    }

    @Test
    void challenge_shouldBeRateLimited() throws Exception {
        int rejected = 0;
        for (int i = 0; i < 15; i++) {
            if (apply("GET", "/api/v1/auth/challenge").getStatus() == 429) rejected++;
        }
        assertThat(rejected).isGreaterThan(0);
    }

    @Test
    void unlockPost_shouldBeRateLimited() throws Exception {
        int rejected = 0;
        for (int i = 0; i < 15; i++) {
            if (apply("POST", "/api/v1/auth/unlock").getStatus() == 429) rejected++;
        }
        assertThat(rejected).isGreaterThan(0);
    }

    @Test
    void unlockGet_wrongMethod_shouldNotBeRateLimited() throws Exception {
        for (int i = 0; i < 15; i++) {
            assertThat(apply("GET", "/api/v1/auth/unlock").getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void nonAuthEndpoints_shouldPassThrough() throws Exception {
        for (int i = 0; i < 15; i++) {
            assertThat(apply("GET", "/api/v1/photos").getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void rateLimitResponse_shouldBeJsonApiResponse() throws Exception {
        MockHttpServletResponse response = null;
        for (int i = 0; i < 15; i++) {
            response = apply("POST", "/api/v1/auth/unlock");
            if (response.getStatus() == 429) break;
        }
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("429").contains("请求过于频繁");
        assertThat(response.getContentType()).contains("application/json");
    }
}
