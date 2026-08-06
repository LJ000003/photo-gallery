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

    private final RateLimitFilter filter = new RateLimitFilter(new ClientIpResolver(""));

    /** 带受信头配置的独立 filter：验证不同客户端 IP 分桶互不影响 */
    private final RateLimitFilter trustedFilter = new RateLimitFilter(new ClientIpResolver("Cf-Connecting-Ip"));

    private MockHttpServletResponse apply(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path); // Tomcat 下 getServletPath() 即去掉 context 后的路径
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
        });
        return response;
    }

    private MockHttpServletResponse applyWithTrustedHeader(String clientIp) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/unlock");
        request.setServletPath("/api/v1/auth/unlock");
        request.addHeader("Cf-Connecting-Ip", clientIp);
        MockHttpServletResponse response = new MockHttpServletResponse();
        trustedFilter.doFilter(request, response, (req, res) -> {
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

    @Test
    void trustedHeader_shouldBucketByClientIp() throws Exception {
        // 同一 IP 超过 10 req/s 被限流
        int rejected = 0;
        for (int i = 0; i < 15; i++) {
            if (applyWithTrustedHeader("203.0.113.9").getStatus() == 429) rejected++;
        }
        assertThat(rejected).isGreaterThan(0);

        // 不同 IP 有独立桶，15 次请求全部放行
        for (int i = 0; i < 15; i++) {
            assertThat(applyWithTrustedHeader("198.51.100." + i).getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void spoofableXff_shouldNotBypassRateLimit() throws Exception {
        // 无受信头时，伪造 X-Real-IP/X-Forwarded-For 不改变限流来源（回退连接地址）
        RateLimitFilter plainFilter = new RateLimitFilter(new ClientIpResolver(""));
        int rejected = 0;
        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/unlock");
            request.setServletPath("/api/v1/auth/unlock");
            request.addHeader("X-Real-IP", "1.2.3.4");
            request.addHeader("X-Forwarded-For", "6.6.6.6");
            MockHttpServletResponse response = new MockHttpServletResponse();
            plainFilter.doFilter(request, response, (req, res) -> {
            });
            if (response.getStatus() == 429) rejected++;
        }
        assertThat(rejected).isGreaterThan(0);
    }
}
