package com.hape.photogallery.controller;

import com.hape.photogallery.dto.BatchPhotoUpdateRequest;
import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.PhotoUpdateRequest;
import com.hape.photogallery.dto.TimelineItem;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.DuplicateException;
import org.springframework.mock.web.MockMultipartFile;
import com.hape.photogallery.service.AlbumService;
import com.hape.photogallery.service.FilePathResolver;
import com.hape.photogallery.service.MigrationService;
import com.hape.photogallery.service.PhotoQueryService;
import com.hape.photogallery.service.PhotoService;
import com.hape.photogallery.service.PhotoTransformService;
import com.hape.photogallery.service.TrashService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.hape.photogallery.repository.ShareTokenRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    @MockBean private ShareTokenRepository shareTokenRepository;

    @Autowired private MockMvc mockMvc;
    @MockBean private PhotoService service;
    @MockBean private PhotoQueryService photoQueryService;
    @MockBean private TrashService trashService;
    @MockBean private AlbumService albumService;
    @MockBean private MigrationService migrationService;
    @MockBean private FilePathResolver filePathResolver;
    @MockBean private PhotoTransformService transformService;

    @TempDir
    Path tempDir;

    // ==================== list ====================

    @Test
    void list_shouldReturnPage() throws Exception {
        when(photoQueryService.listAllResponses(any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/photos?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void list_withFilter_shouldPassParams() throws Exception {
        when(photoQueryService.listAllResponses(any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/photos?page=0&size=20&tagIds=1&tagIds=2&categoryIds=3"))
                .andExpect(status().isOk());
    }

    @Test
    void list_withSearch_shouldCallSearch() throws Exception {
        when(photoQueryService.searchResponses(eq("cat"), isNull(), isNull(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/photos?q=cat"))
                .andExpect(status().isOk());

        verify(photoQueryService).searchResponses("cat", null, null, PageRequest.of(0, 20));
    }

    @Test
    void list_withSearchAndTagFilter_shouldCallSearchWithFilters() throws Exception {
        when(photoQueryService.searchResponses(eq("cat"), eq(List.of(1L)), isNull(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/photos?q=cat&tagIds=1"))
                .andExpect(status().isOk());

        verify(photoQueryService).searchResponses("cat", List.of(1L), null, PageRequest.of(0, 20));
    }

    @Test
    void list_withAlbumId_shouldCallAlbumService() throws Exception {
        when(albumService.listPhotosResponses(eq(5L), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/photos?albumId=5"))
                .andExpect(status().isOk());

        verify(albumService).listPhotosResponses(eq(5L), any());
    }

    @Test
    void list_withAlbumIdZero_shouldCallUnassigned() throws Exception {
        when(albumService.listUnassignedResponses(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/photos?albumId=0"))
                .andExpect(status().isOk());

        verify(albumService).listUnassignedResponses(any());
    }

    // ==================== get ====================

    @Test
    void getById_shouldReturnPhoto() throws Exception {
        Photo p = new Photo(); p.setId(1L); p.setName("照片");
        when(photoQueryService.getPhotoResponse(1L)).thenReturn(PhotoResponse.from(p));

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
        when(photoQueryService.getTimeline(eq("desc"), any(PageRequest.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/photos/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void mapPhotos_shouldReturnList() throws Exception {
        MapItem item = new MapItem();
        when(photoQueryService.getMapPhotos(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
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

    // ==================== thumbnail / webp（回退原图绕过封堵）====================

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getThumbnail_missingThumb_asViewer_should404() throws Exception {
        when(filePathResolver.getByIdIncludeDeleted(1L)).thenReturn(photo("image/jpeg"));
        when(filePathResolver.getThumbnailPath(1L, 400)).thenReturn(null);

        mockMvc.perform(get("/api/v1/photos/1/thumbnail"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("缩略图不存在")));
    }

    @Test
    void getThumbnail_missingThumb_asAdmin_shouldServeOriginal() throws Exception {
        setAdmin();
        when(filePathResolver.getByIdIncludeDeleted(1L)).thenReturn(photo("image/jpeg"));
        when(filePathResolver.getThumbnailPath(1L, 400)).thenReturn(null);
        Path original = createFile("original.jpg");
        when(filePathResolver.getFilePath(1L)).thenReturn(original);

        mockMvc.perform(get("/api/v1/photos/1/thumbnail"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void getThumbnail_thumbExists_shouldServeThumb() throws Exception {
        when(filePathResolver.getByIdIncludeDeleted(1L)).thenReturn(photo("image/jpeg"));
        Path thumb = createFile("thumb.jpg");
        when(filePathResolver.getThumbnailPath(1L, 400)).thenReturn(thumb);

        mockMvc.perform(get("/api/v1/photos/1/thumbnail"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void getThumbnail_invalidWidth_should400() throws Exception {
        mockMvc.perform(get("/api/v1/photos/1/thumbnail?w=9999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void getWebp_missing_asViewer_shouldFallBackToThumbnail() throws Exception {
        // P1 修复：viewer 不因 webp 缺失破图——回退缩略图 400（不能回退原图，view 禁下载）
        when(filePathResolver.getByIdIncludeDeleted(1L)).thenReturn(photo("image/jpeg"));
        when(filePathResolver.getWebpPath(1L)).thenReturn(null);
        Path thumb = createFile("thumb.jpg");
        when(filePathResolver.getThumbnailPath(1L, 400)).thenReturn(thumb);

        mockMvc.perform(get("/api/v1/photos/1/webp"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void getWebp_missingThumb_asViewer_should404() throws Exception {
        // 回退缩略图也缺失时兜底 404（不再返回原图——view 禁下载原图语义不变）
        when(filePathResolver.getByIdIncludeDeleted(1L)).thenReturn(photo("image/jpeg"));
        when(filePathResolver.getWebpPath(1L)).thenReturn(null);
        when(filePathResolver.getThumbnailPath(1L, 400)).thenReturn(null);

        mockMvc.perform(get("/api/v1/photos/1/webp"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("图片不存在")));
    }

    @Test
    void getWebp_missing_asAdmin_shouldServeOriginal() throws Exception {
        setAdmin();
        when(filePathResolver.getByIdIncludeDeleted(1L)).thenReturn(photo("image/jpeg"));
        when(filePathResolver.getWebpPath(1L)).thenReturn(null);
        Path original = createFile("original.jpg");
        when(filePathResolver.getFilePath(1L)).thenReturn(original);

        mockMvc.perform(get("/api/v1/photos/1/webp"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void getWebp_webpExists_shouldServeWebp() throws Exception {
        when(filePathResolver.getByIdIncludeDeleted(1L)).thenReturn(photo("image/jpeg"));
        Path webp = createFile("webp");
        when(filePathResolver.getWebpPath(1L)).thenReturn(webp);

        mockMvc.perform(get("/api/v1/photos/1/webp"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("image/webp")));
    }

    // ==================== upload（补测：成功/409/魔数/超大小） ====================

    @Test
    void upload_shouldReturnPhotoWithProcessingStatus() throws Exception {
        Photo p = new Photo();
        p.setId(1L);
        p.setName("test.jpg");
        p.setProcessingStatus(com.hape.photogallery.entity.ProcessingStatus.PROCESSING);
        when(service.upload(any(), any())).thenReturn(p);
        when(photoQueryService.toResponse(p)).thenReturn(new PhotoResponse());

        MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8});
        mockMvc.perform(multipart("/api/v1/photos").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void upload_duplicate_shouldReturn409WithExistingPhoto() throws Exception {
        PhotoResponse existing = new PhotoResponse();
        when(service.upload(any(), any())).thenThrow(new DuplicateException(existing));

        MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "dup.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8});
        mockMvc.perform(multipart("/api/v1/photos").file(file))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void upload_invalidMagicBytes_shouldReturn400() throws Exception {
        when(service.upload(any(), any()))
                .thenThrow(new com.hape.photogallery.exception.InvalidFileTypeException("文件格式不支持"));

        MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/v1/photos").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void upload_tooLarge_shouldReturn400() throws Exception {
        when(service.upload(any(), any()))
                .thenThrow(new com.hape.photogallery.exception.FileSizeExceededException("文件过大"));

        MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "big.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8});
        mockMvc.perform(multipart("/api/v1/photos").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void batchUpload_shouldReturnUploadedList() throws Exception {
        Photo p = new Photo();
        p.setId(1L);
        when(service.batchUpload(any(), any())).thenReturn(List.of(p));
        when(photoQueryService.toResponse(p)).thenReturn(new PhotoResponse());

        MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "files", "a.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8});
        mockMvc.perform(multipart("/api/v1/photos/batch").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void batchUpload_over50Files_shouldReturn400() throws Exception {
        // 构造 51 个文件 → controller 先于 service 拦截（MAX_BATCH_SIZE = 50）
        var builder = multipart("/api/v1/photos/batch");
        for (int i = 0; i < 51; i++) {
            builder.file(new MockMultipartFile(
                    "files", "a" + i + ".jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8}));
        }
        mockMvc.perform(builder)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("50")));
        verify(service, never()).batchUpload(any(), any());
    }

    // ==================== getFile（补测：正常/缺失/已删权限） ====================

    @Test
    void getFile_shouldServeOriginal() throws Exception {
        Photo p = new Photo();
        p.setId(1L);
        p.setContentType("image/jpeg");
        when(filePathResolver.getByIdIncludeDeleted(1L)).thenReturn(p);
        when(filePathResolver.getFilePath(1L)).thenReturn(createFile("orig.jpg"));

        mockMvc.perform(get("/api/v1/photos/1/file"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void getFile_missingFile_should404() throws Exception {
        Photo p = new Photo();
        p.setId(1L);
        p.setContentType("image/jpeg");
        when(filePathResolver.getByIdIncludeDeleted(1L)).thenReturn(p);
        when(filePathResolver.getFilePath(1L)).thenReturn(tempDir.resolve("nope.jpg"));

        mockMvc.perform(get("/api/v1/photos/1/file"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("文件不存在")));
    }

    @Test
    void getFile_deleted_asViewer_should404() throws Exception {
        Photo p = new Photo();
        p.setId(1L);
        p.setDeletedAt(LocalDateTime.now());
        p.setContentType("image/jpeg");
        when(filePathResolver.getByIdIncludeDeleted(1L)).thenReturn(p);

        mockMvc.perform(get("/api/v1/photos/1/file"))
                .andExpect(status().isNotFound());
        verify(filePathResolver, never()).getFilePath(1L);
    }

    @Test
    void getFile_nullContentType_shouldServeOctetStream() throws Exception {
        // multipart part 无 Content-Type 时落库为 null——parseMediaType(null) 曾抛 IAE → 500
        Photo p = new Photo();
        p.setId(1L);
        p.setContentType(null);
        when(filePathResolver.getByIdIncludeDeleted(1L)).thenReturn(p);
        when(filePathResolver.getFilePath(1L)).thenReturn(createFile("orig.bin"));

        mockMvc.perform(get("/api/v1/photos/1/file"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    void getThumbnail_nullContentType_adminFallback_shouldServeOctetStream() throws Exception {
        setAdmin();
        Photo p = new Photo();
        p.setId(1L);
        p.setContentType(null);
        when(filePathResolver.getByIdIncludeDeleted(1L)).thenReturn(p);
        when(filePathResolver.getThumbnailPath(1L, 400)).thenReturn(null);
        when(filePathResolver.getFilePath(1L)).thenReturn(createFile("orig.bin"));

        mockMvc.perform(get("/api/v1/photos/1/thumbnail"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }

    // ==================== 运维端点（补测） ====================

    @Test
    void restorePhoto_shouldCallTrashService() throws Exception {
        mockMvc.perform(post("/api/v1/photos/1/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(trashService).restore(1L);
    }

    @Test
    void retryProcessing_shouldCallService() throws Exception {
        mockMvc.perform(post("/api/v1/photos/1/retry-processing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(service).retryProcessing(1L);
    }

    @Test
    void transform_shouldCallTransformService() throws Exception {
        mockMvc.perform(post("/api/v1/photos/1/transform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rotate\":90}"))
                .andExpect(status().isOk());
        // mirror 默认 "none"（TransformRequest 字段默认值），旋转 90 时裁剪参数为 null
        verify(transformService).transformPhoto(eq(1L), eq(90), eq("none"), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void extractExif_shouldCallQueryService() throws Exception {
        when(photoQueryService.extractExifForPhoto(1L))
                .thenReturn(new com.hape.photogallery.entity.ExifData());

        mockMvc.perform(post("/api/v1/photos/1/extract-exif"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void migrateThumbnails_shouldReturnGeneratedCount() throws Exception {
        when(migrationService.migrateThumbnails()).thenReturn(7);

        mockMvc.perform(post("/api/v1/photos/migrate-thumbnails"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generated").value(7));
    }

    @Test
    void migrateWebp_shouldReturnGeneratedCount() throws Exception {
        when(migrationService.migrateWebp()).thenReturn(3);

        mockMvc.perform(post("/api/v1/photos/migrate-webp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generated").value(3));
    }

    // ==================== fixMultipartText（multipart 中文乱码恢复） ====================

    @Test
    void fixMultipartText_shouldRecoverUtf8Mojibake() {
        // UTF-8「中文」字节被 ISO-8859-1 解码后的 mojibake → 应恢复为「中文」
        String mojibake = new String("中文".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(PhotoController.fixMultipartText(mojibake)).isEqualTo("中文");
    }

    @Test
    void fixMultipartText_ascii_shouldPassThrough() {
        assertThat(PhotoController.fixMultipartText("hello")).isEqualTo("hello");
        assertThat(PhotoController.fixMultipartText(null)).isNull();
        assertThat(PhotoController.fixMultipartText("")).isEmpty();
    }

    @Test
    void fixMultipartText_invalidUtf8Recovery_shouldKeepOriginal() {
        // 恢复后含 U+FFFD（无效 UTF-8）→ 保留原值，不误伤
        String weird = "aéb"; // é 单字节（非 UTF-8 序列）
        assertThat(PhotoController.fixMultipartText(weird)).isEqualTo(weird);
    }

    private Photo photo(String contentType) {
        Photo p = new Photo();
        p.setId(1L);
        p.setContentType(contentType);
        return p;
    }

    private Path createFile(String name) throws Exception {
        Files.createDirectories(tempDir);
        Path f = tempDir.resolve(name);
        Files.createFile(f);
        return f;
    }

    private void setAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_admin"))));
    }
}
