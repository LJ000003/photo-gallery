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
}
