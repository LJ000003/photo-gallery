package com.hape.photogallery.service;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.repository.PhotoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhotoProcessorTest {

    @Mock private PhotoRepository photoRepo;
    @Mock private ImageProcessingService imageService;
    @Mock private ExifService exifService;

    private PhotoProcessor processor;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        processor = new PhotoProcessor(photoRepo, imageService, exifService);
    }

    @Test
    void process_photoNotFound_shouldReturnEarly() throws Exception {
        when(photoRepo.findById(1L)).thenReturn(Optional.empty());

        processor.process(1L, tempDir.resolve("nope.jpg"), "2024/08", "nope.jpg", null);

        verify(photoRepo, never()).save(any());
    }

    @Test
    void process_validImage_shouldComplete() throws Exception {
        Photo photo = new Photo();
        photo.setId(1L);
        when(photoRepo.findById(1L)).thenReturn(Optional.of(photo));

        // 创建有效的测试图片
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Path imgPath = tempDir.resolve("2024/08");
        Files.createDirectories(imgPath);
        Path target = imgPath.resolve("test.jpg");
        javax.imageio.ImageIO.write(img, "jpg", target.toFile());

        when(imageService.autoRotateIfNeeded(any(), any())).thenReturn(img);
        when(imageService.downscaleToDisplay(any())).thenReturn(img);

        processor.process(1L, target, "2024/08", "test.jpg", null);

        verify(photoRepo, times(1)).save(any(Photo.class));
    }
}
