package com.hape.photogallery.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import com.hape.photogallery.config.ClientIpResolver;
import com.hape.photogallery.config.JwtService;
import com.hape.photogallery.config.MediaSignatureService;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.service.PhotoService;

/**
 * 分享查看 API 测试：白名单生效 + mediaToken 剥离（防 view 权限借签名下载原图）。
 * sharePhotoIds 由 JwtAuthFilter 写入 request attribute（本测试直接注入）。
 * 三件套 @Import：@WebMvcTest 会加载 Filter 类型的 JwtAuthFilter（构造依赖 JwtService + MediaSignatureService）。
 */
@WebMvcTest(value = ShareController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import({ClientIpResolver.class, JwtService.class, MediaSignatureService.class})
class ShareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PhotoService photoService;

    @Test
    void view_shouldReturnWhiteListedPhotosWithoutMediaToken() throws Exception {
        Photo photo = new Photo();
        photo.setId(7L);
        PhotoResponse resp = new PhotoResponse();
        resp.setMediaToken("signed-token-should-be-stripped");
        when(photoService.findByIds(eq(List.of(7L)), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(photo)));
        when(photoService.toResponse(any(Photo.class))).thenReturn(resp);

        mockMvc.perform(get("/api/v1/share/view")
                        .requestAttr("sharePhotoIds", List.of(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0]").exists())
                // 分享上下文必须剥离媒体签名，否则 view 权限可借签名下载原图
                .andExpect(jsonPath("$.data.content[0].mediaToken").doesNotExist());
    }

    @Test
    void view_emptyWhiteList_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/share/view")
                        .requestAttr("sharePhotoIds", List.of()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("分享链接无效或已过期")));
    }

    @Test
    void view_missingWhiteList_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/share/view"))
                .andExpect(status().isNotFound());
    }
}
