package com.example.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PhotoService {

    private final PhotoRepository repo;
    private final Path uploadDir;

    public PhotoService(PhotoRepository repo,
                        @Value("${photo.upload-dir:uploads}") String uploadDir) {
        this.repo = repo;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录: " + this.uploadDir, e);
        }
    }

    public List<Photo> listAll() {
        return repo.findAll();
    }

    public Photo getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new RuntimeException("照片不存在: " + id));
    }

    public Photo upload(MultipartFile file, String name, String description) throws IOException {
        String storedName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path target = uploadDir.resolve(storedName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        Photo photo = new Photo();
        photo.setName(name != null && !name.isBlank() ? name : file.getOriginalFilename());
        photo.setDescription(description);
        photo.setFileName(storedName);
        photo.setOriginalFileName(file.getOriginalFilename());
        photo.setFileSize(file.getSize());
        photo.setContentType(file.getContentType());
        photo.setCreatedAt(LocalDateTime.now());

        return repo.save(photo);
    }

    public Photo update(Long id, String name, String description) {
        Photo photo = getById(id);
        if (name != null && !name.isBlank()) {
            photo.setName(name);
        }
        if (description != null) {
            photo.setDescription(description);
        }
        return repo.save(photo);
    }

    public void delete(Long id) {
        Photo photo = getById(id);
        try {
            Files.deleteIfExists(uploadDir.resolve(photo.getFileName()));
        } catch (IOException e) {
            // 文件删除失败不影响数据库记录删除
        }
        repo.delete(photo);
    }

    public Path getFilePath(Long id) {
        Photo photo = getById(id);
        return uploadDir.resolve(photo.getFileName());
    }
}
