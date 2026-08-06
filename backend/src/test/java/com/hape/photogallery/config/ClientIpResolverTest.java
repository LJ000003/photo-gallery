package com.hape.photogallery.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IP 解析策略测试：只信任配置的受信头，头缺失/非法一律回退连接地址，
 * 可伪造的 X-Forwarded-For 永不信任。
 */
class ClientIpResolverTest {

    @Test
    void trustedHeader_shouldBeUsed() {
        ClientIpResolver resolver = new ClientIpResolver("Cf-Connecting-Ip");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Cf-Connecting-Ip", "203.0.113.9");
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void trustedHeaderMissing_shouldFallbackToRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver("Cf-Connecting-Ip");
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(resolver.resolve(request)).isEqualTo(request.getRemoteAddr());
    }

    @Test
    void trustedHeaderInvalidIp_shouldFallbackToRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver("Cf-Connecting-Ip");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Cf-Connecting-Ip", "999.999.999.999");
        assertThat(resolver.resolve(request)).isEqualTo(request.getRemoteAddr());
    }

    @Test
    void xff_shouldNeverBeTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("Cf-Connecting-Ip");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        // 受信头缺失时回退连接地址，而不是可伪造的 XFF
        assertThat(resolver.resolve(request)).isEqualTo(request.getRemoteAddr());
    }

    @Test
    void noTrustedHeader_shouldUseRemoteAddrOnly() {
        ClientIpResolver resolver = new ClientIpResolver("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Cf-Connecting-Ip", "203.0.113.9");
        request.addHeader("X-Real-IP", "198.51.100.7");
        assertThat(resolver.resolve(request)).isEqualTo(request.getRemoteAddr());
    }
}
