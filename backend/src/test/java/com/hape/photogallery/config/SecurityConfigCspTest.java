package com.hape.photogallery.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CSP 安全头验证（全上下文启动：H2 + dev profile，surefire 注入 JWT_SECRET）。
 * 只用公开端点（/api/v1/auth/challenge permitAll），避免依赖静态构建产物。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigCspTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicEndpoint_shouldCarryCspHeader() throws Exception {
        mockMvc.perform(get("/api/v1/auth/challenge"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("img-src 'self' data: blob: https://*.is.autonavi.com")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("style-src 'self' 'unsafe-inline'")));
    }
}
