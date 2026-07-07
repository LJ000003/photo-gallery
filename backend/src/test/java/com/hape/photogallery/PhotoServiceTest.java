package com.hape.photogallery;

import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.PhotoUpdateRequest;
import com.hape.photogallery.dto.TimelineItem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock private PhotoRepository photoRepo;
    @Mock private TagRepository tagRepo;
    @Mock private CategoryRepository catRepo;
    @Mock private ExifDataRepository exifRepo;
    @Mock private ExifService exifService;
    @Mock private ImageProcessingService imageService;
    @Mock private AlbumService albumService;

    @TempDir Path tempDir;

    private PhotoService service;

    // minimal valid JPEG header
    private static final byte[] JPEG_BYTES = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
        0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };

    @BeforeEach
    void setUp() throws IOException {
        service = new PhotoService(photoRepo, tagRepo, catRepo, exifRepo, exifService,
                imageService, albumService, tempDir.toString());
    }

    // ==================== listAll ====================

    @Test
    void listAll_shouldCallRepository() {
        Page<Photo> page = new PageImpl<>(List.of());
        when(photoRepo.findAll(any(PageRequest.class))).thenReturn(page);
        Page<Photo> result = service.listAll(null, null, PageRequest.of(0, 20));
        assertThat(result).isSameAs(page);
    }

    @Test
    void listAll_withTag_shouldCallFindByTagIds() {
        when(photoRepo.findByTagIds(any(), any())).thenReturn(new PageImpl<>(List.of()));
        service.listAll(List.of(1L), null, PageRequest.of(0, 20));
        verify(photoRepo).findByTagIds(List.of(1L), PageRequest.of(0, 20));
        verify(photoRepo, never()).findAll(any(PageRequest.class));
    }

    @Test
    void listAll_withCategory_shouldCallFindByCategoryIds() {
        when(photoRepo.findByCategoryIds(any(), any())).thenReturn(new PageImpl<>(List.of()));
        service.listAll(null, List.of(2L), PageRequest.of(0, 20));
        verify(photoRepo).findByCategoryIds(List.of(2L), PageRequest.of(0, 20));
    }

    @Test
    void listAll_withBoth_shouldCallCombinedQuery() {
        when(photoRepo.findByCategoryIdsAndTagIds(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        service.listAll(List.of(1L), List.of(2L), PageRequest.of(0, 20));
        verify(photoRepo).findByCategoryIdsAndTagIds(List.of(2L), List.of(1L), PageRequest.of(0, 20));
    }

    // ==================== search ====================

    @Test
    void search_shouldCallRepository() {
        when(photoRepo.search(eq("cat"), any())).thenReturn(new PageImpl<>(List.of()));
        service.search("cat", PageRequest.of(0, 20));
        verify(photoRepo).search("cat", PageRequest.of(0, 20));
    }

    @Test
    void search_blankQuery_shouldFallbackToFindAll() {
        when(photoRepo.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));
        service.search("  ", PageRequest.of(0, 20));
        verify(photoRepo).findAll(any(PageRequest.class));
    }

    @Test
    void search_nullQuery_shouldFallbackToFindAll() {
        when(photoRepo.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));
        service.search(null, PageRequest.of(0, 20));
        verify(photoRepo).findAll(any(PageRequest.class));
    }

    // ==================== getById ====================

    @Test
    void getById_found_shouldReturnPhoto() {
        Photo p = new Photo(); p.setId(1L); p.setName("test");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        assertThat(service.getById(1L).getName()).isEqualTo("test");
    }

    @Test
    void getById_notFound_shouldThrow() {
        when(photoRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(99L)).hasMessageContaining("不存在");
    }

    // ==================== findByIds ====================

    @Test
    void findByIds_shouldCallRepository() {
        when(photoRepo.findByIdIn(any(), any())).thenReturn(new PageImpl<>(List.of()));
        service.findByIds(List.of(1L, 2L), PageRequest.of(0, 20));
        verify(photoRepo).findByIdIn(List.of(1L, 2L), PageRequest.of(0, 20));
    }

    // ==================== upload ====================

    @Test
    void upload_shouldSavePhotoAndProcessImage() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", JPEG_BYTES);
        when(photoRepo.save(any(Photo.class))).thenAnswer(inv -> {
            Photo p = inv.getArgument(0);
            if (p.getId() == null) { p.setId(1L); p.setFileName("2026/07/test.jpg"); }
            return p;
        });
        when(tagRepo.findAllById(any())).thenReturn(List.of());
        when(exifService.extractAndSave(any(), any())).thenReturn(null);

        Photo result = service.upload(file, "test", "desc", List.of(1L), 5L, "watermark");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("test");
        verify(photoRepo).save(any());
        verify(imageService).validateImageMagicBytes(any());
        verify(imageService).autoRotateIfNeeded(any());
        verify(imageService).applyWatermark(any(), eq("watermark"));
        verify(imageService).generateThumbnail(any(), any(), any());
        verify(imageService).generateThumbnail(any(), any(), any(), eq(200));
        verify(imageService).generateWebp(any(), any(), any());
    }

    @Test
    void upload_fileTooLarge_shouldThrow() {
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", new byte[11 * 1024 * 1024]);
        assertThatThrownBy(() -> service.upload(file, "big", null, null, null, null))
                .isInstanceOf(FileSizeExceededException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    void upload_emptyName_shouldUseOriginalFileName() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "original.jpg", "image/jpeg", JPEG_BYTES);
        when(photoRepo.save(any(Photo.class))).thenAnswer(inv -> {
            Photo p = inv.getArgument(0);
            if (p.getId() == null) { p.setId(1L); p.setFileName("2026/07/test.jpg"); }
            return p;
        });
        when(exifService.extractAndSave(any(), any())).thenReturn(null);

        Photo result = service.upload(file, null, null, null, null, null);
        assertThat(result.getName()).isEqualTo("original.jpg");
    }

    // ==================== batchUpload ====================

    @Test
    void batchUpload_shouldUploadEachFile() throws IOException {
        MockMultipartFile f1 = new MockMultipartFile("files", "a.jpg", "image/jpeg", JPEG_BYTES);
        MockMultipartFile f2 = new MockMultipartFile("files", "b.jpg", "image/jpeg", JPEG_BYTES);
        when(photoRepo.save(any(Photo.class))).thenAnswer(inv -> {
            Photo p = inv.getArgument(0); p.setId(1L); return p;
        });
        when(exifService.extractAndSave(any(), any())).thenReturn(null);

        List<Photo> results = service.batchUpload(List.of(f1, f2), "batch", null, null, null, null);
        assertThat(results).hasSize(2);
    }

    // ==================== update ====================

    @Test
    void update_shouldModifyFields() {
        Photo p = new Photo(); p.setId(1L); p.setName("old");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(photoRepo.save(any())).thenReturn(p);

        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setName("newName");
        req.setDescription("newDesc");

        PhotoResponse result = service.update(1L, req);
        assertThat(result.getName()).isEqualTo("newName");
        assertThat(result.getDescription()).isEqualTo("newDesc");
    }

    @Test
    void update_withTags_shouldSetTags() {
        Photo p = new Photo(); p.setId(1L); p.setName("p");
        Tag t = new Tag("tag", "#fff"); t.setId(1L);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(photoRepo.save(any())).thenReturn(p);
        when(tagRepo.findAllById(List.of(1L))).thenReturn(List.of(t));

        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setName("p");
        req.setTagIds(List.of(1L));

        PhotoResponse result = service.update(1L, req);
        assertThat(result.getTags()).hasSize(1);
    }

    @Test
    void update_nullCategory_shouldClearCategory() {
        Photo p = new Photo(); p.setId(1L); p.setName("p");
        Category c = new Category("cat"); c.setId(5L);
        p.setCategory(c);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(photoRepo.save(any())).thenReturn(p);

        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setName("p");
        req.setCategoryId(null);

        PhotoResponse result = service.update(1L, req);
        assertThat(result.getCategory()).isNull();
    }

    // ==================== delete ====================

    @Test
    void delete_shouldRemovePhotoAndFiles() throws IOException {
        Path filePath = tempDir.resolve("2026/07/test.jpg");
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, JPEG_BYTES);

        Photo p = new Photo(); p.setId(1L); p.setName("test");
        p.setFileName("2026/07/test.jpg");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(exifRepo.findByPhoto_Id(1L)).thenReturn(Optional.empty());

        service.delete(1L);

        verify(photoRepo).delete(p);
        assertThat(Files.exists(filePath)).isFalse();
    }

    @Test
    void delete_withExif_shouldDeleteExif() {
        Photo p = new Photo(); p.setId(1L); p.setName("test");
        p.setFileName("2026/07/test.jpg");
        ExifData exif = new ExifData(); exif.setId(10L);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        when(exifRepo.findByPhoto_Id(1L)).thenReturn(Optional.of(exif));

        service.delete(1L);

        verify(exifRepo).delete(exif);
        verify(photoRepo).delete(p);
    }

    // ==================== batchDelete ====================

    @Test
    void batchDelete_shouldReturnCount() {
        Photo p1 = new Photo(); p1.setId(1L); p1.setName("a"); p1.setFileName("2026/07/a.jpg");
        Photo p2 = new Photo(); p2.setId(2L); p2.setName("b"); p2.setFileName("2026/07/b.jpg");
        when(photoRepo.findAllById(List.of(1L, 2L))).thenReturn(List.of(p1, p2));

        int count = service.batchDelete(List.of(1L, 2L));
        assertThat(count).isEqualTo(2);
    }

    @Test
    void batchDelete_skipMissing() {
        when(photoRepo.findAllById(List.of(1L))).thenReturn(List.of());

        int count = service.batchDelete(List.of(1L));
        assertThat(count).isEqualTo(0);
    }

    // ==================== getTimeline ====================

    @Test
    void getTimeline_desc_shouldReturnMappedItems() {
        ExifData e = new ExifData();
        e.setId(1L);
        Photo p = new Photo(); p.setId(10L); p.setName("p1");
        e.setPhoto(p);
        e.setDateTaken(LocalDateTime.of(2026, 1, 15, 10, 0));
        e.setCameraModel("Canon");
        when(exifRepo.findWithDateTakenAndPhotoDesc()).thenReturn(List.of(e));

        List<TimelineItem> items = service.getTimeline("desc");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPhotoId()).isEqualTo(10L);
        assertThat(items.get(0).getCameraModel()).isEqualTo("Canon");
    }

    @Test
    void getTimeline_asc_shouldCallAsc() {
        when(exifRepo.findWithDateTakenAndPhotoAsc()).thenReturn(List.of());
        service.getTimeline("asc");
        verify(exifRepo).findWithDateTakenAndPhotoAsc();
    }

    // ==================== getMapPhotos ====================

    @Test
    void getMapPhotos_shouldTransformCoordinates() {
        ExifData e = new ExifData();
        e.setLatitude(39.9); e.setLongitude(116.4);
        Photo p = new Photo(); p.setId(10L); p.setName("map1");
        e.setPhoto(p);
        when(exifRepo.findWithGpsAndPhoto()).thenReturn(List.of(e));

        List<MapItem> items = service.getMapPhotos();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPhotoId()).isEqualTo(10L);
        // coordinates should have been transformed from WGS84 to GCJ02
        assertThat(items.get(0).getLatitude()).isNotEqualTo(39.9);
        assertThat(items.get(0).getLongitude()).isNotEqualTo(116.4);
    }

    // ==================== extractExif ====================

    @Test
    void extractExifForExisting_shouldProcessAllPhotos() {
        when(photoRepo.findAll()).thenReturn(List.of());
        int count = service.extractExifForExisting();
        assertThat(count).isEqualTo(0);
    }

    @Test
    void extractExifForPhoto_notFound_shouldReturnNull() {
        Photo p = new Photo(); p.setId(1L); p.setName("p");
        p.setFileName("nonexistent.jpg");
        when(photoRepo.findById(1L)).thenReturn(Optional.of(p));
        assertThat(service.extractExifForPhoto(1L)).isNull();
    }

    // ==================== DTO conversion ====================

    @Test
    void toResponse_shouldMapAllFields() {
        Photo p = new Photo(); p.setId(1L); p.setName("p1");
        p.setDescription("desc"); p.setFileSize(100L);
        p.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        Category c = new Category("cat"); c.setId(1L);
        p.setCategory(c);

        PhotoResponse r = service.toResponse(p);
        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getName()).isEqualTo("p1");
        assertThat(r.getDescription()).isEqualTo("desc");
        assertThat(r.getFileSize()).isEqualTo(100L);
        assertThat(r.getCategory().getId()).isEqualTo(1L);
    }
}
