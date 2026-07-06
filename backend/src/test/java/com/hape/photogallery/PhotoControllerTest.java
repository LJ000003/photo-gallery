package com.hape.photogallery;

import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.PhotoUpdateRequest;
import com.hape.photogallery.dto.TimelineItem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = PhotoController.class,
            excludeAutoConfiguration = SecurityAutoConfiguration.class)
class PhotoControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private PhotoService service;
    @MockBean private AlbumService albumService;

    // ==================== list ====================

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
    void list_withSearch_shouldCallSearch() throws Exception {
        when(service.search(eq("cat"), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/photos?q=cat"))
                .andExpect(status().isOk());

        verify(service).search("cat", PageRequest.of(0, 20));
    }

    @Test
    void list_withAlbumId_shouldCallAlbumService() throws Exception {
        when(albumService.listPhotos(eq(5L), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/photos?albumId=5"))
                .andExpect(status().isOk());

        verify(albumService).listPhotos(eq(5L), any());
    }

    @Test
    void list_withAlbumIdZero_shouldCallUnassigned() throws Exception {
        when(albumService.listUnassigned(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/photos?albumId=0"))
                .andExpect(status().isOk());

        verify(albumService).listUnassigned(any());
    }

    // ==================== get ====================

    @Test
    void getById_shouldReturnPhoto() throws Exception {
        Photo p = new Photo(); p.setId(1L); p.setName("照片");
        when(service.getById(1L)).thenReturn(p);
        when(service.toResponse(p)).thenReturn(PhotoResponse.from(p));

        mockMvc.perform(get("/api/photos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("照片"));
    }

    // ==================== update ====================

    @Test
    void update_shouldReturnUpdatedPhoto() throws Exception {
        Photo p = new Photo(); p.setId(1L); p.setName("更新");
        when(service.update(eq(1L), any(PhotoUpdateRequest.class)))
                .thenReturn(PhotoResponse.from(p));

        mockMvc.perform(put("/api/photos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"更新\",\"description\":\"\",\"tagIds\":[],\"categoryId\":null,\"albumIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("更新"));
    }

    @Test
    void update_blankName_shouldReturn400() throws Exception {
        mockMvc.perform(put("/api/photos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== delete ====================

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

    // ==================== timeline & map ====================

    @Test
    void timeline_shouldReturnList() throws Exception {
        TimelineItem item = new TimelineItem();
        when(service.getTimeline(eq("desc"))).thenReturn(List.of(item));

        mockMvc.perform(get("/api/photos/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void mapPhotos_shouldReturnList() throws Exception {
        MapItem item = new MapItem();
        when(service.getMapPhotos()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/photos/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== EXIF utility endpoints ====================

    @Test
    void extractExifBatch_shouldReturnCount() throws Exception {
        when(service.extractExifForExisting()).thenReturn(5);

        mockMvc.perform(post("/api/photos/extract-exif"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.extracted").value(5));
    }
}
