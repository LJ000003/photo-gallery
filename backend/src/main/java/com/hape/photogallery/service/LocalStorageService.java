package com.hape.photogallery.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalStorageService implements StorageService {

    private final Path uploadDir;

    public LocalStorageService(@Value("${photo.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录: " + this.uploadDir, e);
        }
    }

    @Override
    public Path getUploadDir() {
        return uploadDir;
    }

    @Override
    public Path resolveSafe(String relativePath) {
        Path resolved = uploadDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(uploadDir)) {
            throw new SecurityException("Invalid file path");
        }
        return resolved;
    }

    @Override
    public void store(MultipartFile file, Path target) throws IOException {
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void deleteFile(String relativePath) {
        try {
            Files.deleteIfExists(resolveSafe(relativePath));
        } catch (IOException ignored) {}
    }

    @Override
    public boolean exists(String relativePath) {
        return Files.exists(uploadDir.resolve(relativePath));
    }

    @Override
    public void createDirectories(Path dir) throws IOException {
        Files.createDirectories(dir);
    }
}
