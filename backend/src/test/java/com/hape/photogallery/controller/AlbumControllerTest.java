package com.hape.photogallery.controller;

import com.hape.photogallery.service.AlbumService;
import com.hape.photogallery.service.PhotoQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.hape.photogallery.repository.ShareTokenRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AlbumController.class,
            excludeAutoConfiguration = SecurityAutoConfiguration.class)
@org.springframework.context.annotation.Import({com.hape.photogallery.config.JwtService.class, com.hape.photogallery.config.ClientIpResolver.class, com.hape.photogallery.config.MediaSignatureService.class})
class AlbumControllerTest {
    @MockBean private ShareTokenRepository shareTokenRepository;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlbumService albumService;

    @MockBean
    private PhotoQueryService photoQueryService;

    @Test
    void listAlbumPhotoIds_shouldReturnIdsOnly() throws Exception {
        when(albumService.listPhotoIds(1L)).thenReturn(List.of(3L, 7L, 11L));

        mockMvc.perform(get("/api/v1/albums/1/photo-ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0]").value(3))
                .andExpect(jsonPath("$.data[2]").value(11));
    }

    /** 相册详情端点（补缺：此前 GET /albums/{id} 405 → 兜底 500） */
    @Test
    void getAlbum_shouldReturnDetailWithMediaToken() throws Exception {
        com.hape.photogallery.dto.AlbumResponse album = new com.hape.photogallery.dto.AlbumResponse();
        album.setId(1L);
        album.setCoverPhotoId(5L);
        when(albumService.getAlbum(1L)).thenReturn(album);

        mockMvc.perform(get("/api/v1/albums/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.coverPhotoId").value(5))
                .andExpect(jsonPath("$.data.mediaToken").isNotEmpty());
    }

    @Test
    void getAlbum_notFound_should404() throws Exception {
        when(albumService.getAlbum(99L))
                .thenThrow(new com.hape.photogallery.exception.BusinessException(404, "相册不存在"));

        mockMvc.perform(get("/api/v1/albums/99"))
                .andExpect(status().isNotFound());
    }
}
