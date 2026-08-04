package com.hape.photogallery.controller;

import com.hape.photogallery.dto.BatchPhotoUpdateRequest;
import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.PhotoUpdateRequest;
import com.hape.photogallery.dto.TimelineItem;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.service.AlbumService;
import com.hape.photogallery.service.FilePathResolver;
import com.hape.photogallery.service.MigrationService;
import com.hape.photogallery.service.PhotoService;
import com.hape.photogallery.service.PhotoTransformService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = PhotoController.class,
            excludeAutoConfiguration = SecurityAutoConfiguration.class)
@org.springframework.context.annotation.Import({com.hape.photogallery.config.JwtService.class, com.hape.photogallery.config.ClientIpResolver.class, com.hape.photogallery.config.MediaSignatureService.class})
class PhotoControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private PhotoService service;
    @MockBean private AlbumService albumService;
    @MockBean private MigrationService migrationService;
    @MockBean private FilePathResolver filePathResolver;
    @MockBean private PhotoTransformService transformService;

    // ==================== list ====================

    @Test
    void list_shouldReturnPage() throws Exception {
        when(service.listAllResponses(any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/photos?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void list_withFilter_shouldPassParams() throws Exception {
        when(service.listAllResponses(any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/photos?page=0&size=20&tagIds=1&tagIds=2&categoryIds=3"))
                .andExpect(status().isOk());
    }

    @Test
    void list_withSearch_shouldCallSearch() throws Exception {
        when(service.search(eq("cat"), isNull(), isNull(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/photos?q=cat"))
                .andExpect(status().isOk());

        verify(service).search("cat", null, null, PageRequest.of(0, 20));
    }

    @Test
    void list_withSearchAndTagFilter_shouldCallSearchWithFilters() throws Exception {
        when(service.search(eq("cat"), eq(List.of(1L)), isNull(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/photos?q=cat&tagIds=1"))
                .andExpect(status().isOk());

        verify(service).search("cat", List.of(1L), null, PageRequest.of(0, 20));
    }

    @Test
    void list_withAlbumId_shouldCallAlbumService() throws Exception {
        when(albumService.listPhotos(eq(5L), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/photos?albumId=5"))
                .andExpect(status().isOk());

        verify(albumService).listPhotos(eq(5L), any());
    }

    @Test
    void list_withAlbumIdZero_shouldCallUnassigned() throws Exception {
        when(albumService.listUnassigned(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/photos?albumId=0"))
                .andExpect(status().isOk());

        verify(albumService).listUnassigned(any());
    }

    // ==================== get ====================

    @Test
    void getById_shouldReturnPhoto() throws Exception {
        Photo p = new Photo(); p.setId(1L); p.setName("照片");
        when(service.getPhotoResponse(1L)).thenReturn(PhotoResponse.from(p));

        mockMvc.perform(get("/api/v1/photos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("照片"));
    }

    // ==================== update ====================

    @Test
    void update_shouldReturnUpdatedPhoto() throws Exception {
        Photo p = new Photo(); p.setId(1L); p.setName("更新");
        when(service.update(eq(1L), any(PhotoUpdateRequest.class)))
                .thenReturn(PhotoResponse.from(p));

        mockMvc.perform(put("/api/v1/photos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"更新\",\"description\":\"\",\"tagIds\":[],\"categoryId\":null,\"albumIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("更新"));
    }

    @Test
    void update_blankName_shouldReturn400() throws Exception {
        mockMvc.perform(put("/api/v1/photos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== delete ====================

    @Test
    void delete_shouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/photos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("删除成功"));
    }

    @Test
    void batchDelete_shouldReturnCount() throws Exception {
        when(service.batchDelete(List.of(1L, 2L))).thenReturn(2);

        mockMvc.perform(delete("/api/v1/photos/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1,2]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(2));
    }

    // ==================== batchUpdate ====================

    @Test
    void batchUpdate_shouldReturnUpdatedPhotos() throws Exception {
        Photo p = new Photo(); p.setId(1L); p.setName("更新");
        when(service.batchUpdate(any(BatchPhotoUpdateRequest.class)))
                .thenReturn(List.of(PhotoResponse.from(p)));

        mockMvc.perform(put("/api/v1/photos/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoIds\":[1],\"addTagIds\":[],\"removeTagIds\":[]," +
                                "\"addAlbumIds\":[],\"removeAlbumIds\":[],\"categoryOp\":\"NONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("更新"));
    }

    @Test
    void batchUpdate_emptyPhotoIds_shouldReturn400() throws Exception {
        mockMvc.perform(put("/api/v1/photos/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchUpdate_over50PhotoIds_shouldReturn400() throws Exception {
        String ids = "[" + String.join(",", java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(String::valueOf).toList()) + "]";
        mockMvc.perform(put("/api/v1/photos/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoIds\":" + ids + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchUpdate_setWithoutCategory_shouldReturn400() throws Exception {
        mockMvc.perform(put("/api/v1/photos/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoIds\":[1],\"categoryOp\":\"SET\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchUpdate_invalidCategoryOp_shouldReturn400() throws Exception {
        mockMvc.perform(put("/api/v1/photos/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoIds\":[1],\"categoryOp\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== timeline & map ====================

    @Test
    void timeline_shouldReturnList() throws Exception {
        when(service.getTimeline(eq("desc"), any(PageRequest.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/photos/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void mapPhotos_shouldReturnList() throws Exception {
        MapItem item = new MapItem();
        when(service.getMapPhotos(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/photos/map?swLat=30&swLng=100&neLat=50&neLng=130"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 参数错误 → 400（缺陷 1 回归）====================

    @Test
    void mapPhotos_missingParams_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/photos/map"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("swLat")));
    }

    @Test
    void mapPhotos_typeMismatch_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/photos/map?swLat=abc&swLng=-180&neLat=90&neLng=180"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("swLat")));
    }

    @Test
    void list_typeMismatchTagIds_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/photos?tagIds=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void upload_missingFile_shouldReturn400() throws Exception {
        mockMvc.perform(multipart("/api/v1/photos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void upload_nonMultipartRequest_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/photos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("multipart")));
    }

    // ==================== EXIF utility endpoints ====================

    @Test
    void extractExifBatch_shouldReturnCount() throws Exception {
        when(migrationService.extractExifForExisting()).thenReturn(5);

        mockMvc.perform(post("/api/v1/photos/extract-exif"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.extracted").value(5));
    }
}
