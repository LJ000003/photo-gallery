package com.hape.photogallery.service;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    Path getUploadDir();
    Path resolveSafe(String relativePath);
    void store(MultipartFile file, Path target) throws IOException;
    void deleteFile(String relativePath);
    boolean exists(String relativePath);
    void createDirectories(Path dir) throws IOException;
}
