package com.hape.photogallery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = PhotoController.class,
            excludeAutoConfiguration = SecurityAutoConfiguration.class)
class PhotoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PhotoService service;

    @MockBean
    private AlbumService albumService;

    @Test
    void list_shouldReturnPage() throws Exception {
        when(service.listAll(any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/photos?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void list_withFilter_shouldPassParams() throws Exception {
        when(service.listAll(any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/photos?page=0&size=20&tagIds=1&tagIds=2&categoryIds=3"))
                .andExpect(status().isOk());
    }

    @Test
    void getById_shouldReturnPhoto() throws Exception {
        Photo p = new Photo(); p.setId(1L); p.setName("测试照片");
        when(service.getById(1L)).thenReturn(p);

        mockMvc.perform(get("/api/photos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("测试照片"));
    }

    @Test
    void delete_shouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/api/photos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("删除成功"));
    }

    @Test
    void batchDelete_shouldReturnCount() throws Exception {
        when(service.batchDelete(List.of(1L, 2L))).thenReturn(2);

        mockMvc.perform(delete("/api/photos/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1,2]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(2));
    }
}
