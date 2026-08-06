package com.hape.photogallery.controller;

import com.hape.photogallery.dto.AlbumResponse;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.entity.Album;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.ShareTokenRepository;
import com.hape.photogallery.service.AlbumService;
import com.hape.photogallery.service.PhotoQueryService;
import com.hape.photogallery.service.TrashService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 回收站端点切片测试（P1-#14：回收站恢复边界）。
 * 基建照抄 PhotoControllerTest：@WebMvcTest 会注册 @Component 过滤器
 * （JwtAuthFilter/RateLimitFilter），其依赖 JwtService/MediaSignatureService/ShareTokenRepository
 * 必须显式提供。
 * 授权边界（未认证 401 / admin 到达 controller 404）见 config/TrashAuthTest。
 */
@WebMvcTest(value = TrashController.class,
            excludeAutoConfiguration = SecurityAutoConfiguration.class)
@org.springframework.context.annotation.Import({com.hape.photogallery.config.JwtService.class,
        com.hape.photogallery.config.ClientIpResolver.class,
        com.hape.photogallery.config.MediaSignatureService.class})
class TrashControllerTest {

    @MockBean private ShareTokenRepository shareTokenRepository;

    @Autowired private MockMvc mockMvc;
    @MockBean private TrashService trashService;
    @MockBean private PhotoQueryService photoQueryService;
    @MockBean private AlbumService albumService;

    // ==================== 回收站照片列表 ====================

    @Test
    void listPhotos_shouldReturnPage() throws Exception {
        when(trashService.listDeleted(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/trash/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void listPhotos_shouldPassPageable() throws Exception {
        when(trashService.listDeleted(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/trash/photos?page=1&size=5"))
                .andExpect(status().isOk());

        verify(trashService).listDeleted(PageRequest.of(1, 5));
    }

    @Test
    void listPhotos_shouldMapToResponse() throws Exception {
        Photo p = new Photo();
        p.setId(1L);
        p.setName("已删");
        when(trashService.listDeleted(any())).thenReturn(new PageImpl<>(List.of(p)));
        when(photoQueryService.toResponse(any())).thenReturn(PhotoResponse.from(p));

        mockMvc.perform(get("/api/v1/trash/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("已删"));
    }

    // ==================== 回收站相册列表 ====================

    @Test
    void listAlbums_shouldReturnList() throws Exception {
        Album a = new Album("已删相册");
        a.setId(1L);
        when(albumService.listDeleted()).thenReturn(List.of(AlbumResponse.from(a, 0)));

        mockMvc.perform(get("/api/v1/trash/albums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("已删相册"))
                // P4-#38：回收站 UI 不显示计数，photoCount 恒 0
                .andExpect(jsonPath("$.data[0].photoCount").value(0));
    }

    // ==================== 照片恢复 / 永久删除 ====================

    @Test
    void restorePhoto_shouldCallService() throws Exception {
        mockMvc.perform(post("/api/v1/trash/photos/1/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("恢复成功"));

        verify(trashService).restore(1L);
    }

    @Test
    void restorePhoto_notFound_should404() throws Exception {
        doThrow(new BusinessException(404, "未找到可恢复的照片")).when(trashService).restore(99L);

        mockMvc.perform(post("/api/v1/trash/photos/99/restore"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("未找到可恢复的照片")));
    }

    @Test
    void permanentlyDeletePhoto_shouldCallService() throws Exception {
        mockMvc.perform(delete("/api/v1/trash/photos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("已彻底删除"));

        verify(trashService).permanentlyDelete(1L);
    }

    @Test
    void permanentlyDeletePhoto_notFound_should404() throws Exception {
        doThrow(new BusinessException(404, "未找到该照片")).when(trashService).permanentlyDelete(99L);

        mockMvc.perform(delete("/api/v1/trash/photos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ==================== 相册恢复 / 永久删除 ====================

    @Test
    void restoreAlbum_shouldCallService() throws Exception {
        mockMvc.perform(post("/api/v1/trash/albums/1/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("恢复成功"));

        verify(albumService).restore(1L);
    }

    @Test
    void restoreAlbum_notFound_should404() throws Exception {
        doThrow(new BusinessException(404, "未找到可恢复的相册")).when(albumService).restore(99L);

        mockMvc.perform(post("/api/v1/trash/albums/99/restore"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void permanentlyDeleteAlbum_shouldCallService() throws Exception {
        mockMvc.perform(delete("/api/v1/trash/albums/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("已彻底删除"));

        verify(albumService).permanentlyDelete(1L);
    }

    @Test
    void permanentlyDeleteAlbum_notFound_should404() throws Exception {
        doThrow(new BusinessException(404, "未找到该相册")).when(albumService).permanentlyDelete(99L);

        mockMvc.perform(delete("/api/v1/trash/albums/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ==================== 参数错误（附带断言） ====================

    @Test
    void pathParamTypeMismatch_should400() throws Exception {
        // 框架类型转换失败由 GlobalExceptionHandler 映射为 400（带参数名），而非 500
        mockMvc.perform(post("/api/v1/trash/photos/abc/restore"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("id")));
    }
}
