package com.hape.photogallery.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 回收站端点授权边界（P1-#14，全上下文启动：H2 + dev profile，surefire 注入 JWT_SECRET）。
 *
 * 「401 vs 404」区分授权层拦截与到达 controller：
 * - 未认证 → 401（SecurityConfig 自定义 authenticationEntryPoint）
 * - 有效 admin JWT → 404（通过过滤器链、到达 controller、服务层查无此照片）
 * 不做 403 断言：403 需要「已认证但缺 ROLE_admin」的身份，系统只有 admin 角色、
 * viewer JWT 已停止签发（issueShare 删除，JwtService 仅 issueAdmin），无法干净构造。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TrashAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void unauthenticated_restore_should401() throws Exception {
        mockMvc.perform(post("/api/v1/trash/photos/1/restore"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void adminToken_shouldReachController_should404() throws Exception {
        String token = jwtService.issueAdmin(60_000);

        mockMvc.perform(delete("/api/v1/trash/photos/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("未找到该照片")));
    }
}
