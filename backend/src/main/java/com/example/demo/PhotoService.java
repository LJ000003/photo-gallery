package com.example.demo;

import java.io.IOException;
import java.io.InputStream;
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
                .orElseThrow(() -> new RuntimeException("该照片已被删除或不存在"));
    }

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public Photo upload(MultipartFile file, String name, String description) throws IOException {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileSizeExceededException("文件过大，请上传小于 10MB 的图片");
        }
        validateImageMagicBytes(file.getInputStream());

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

    private void validateImageMagicBytes(InputStream in) throws IOException {
        byte[] header = new byte[12];
        int read = in.read(header);

        if (read <= 0) {
            throw new InvalidFileTypeException("无法识别文件内容，请上传图片文件");
        }

        if (read >= 2
                && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8) {
            return; // JPEG
        }
        if (read >= 4
                && header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return; // PNG
        }
        if (read >= 4
                && header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38) {
            return; // GIF
        }
        if (read >= 2
                && header[0] == 0x42 && header[1] == 0x4D) {
            return; // BMP
        }
        if (read >= 12
                && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
            return; // WebP
        }

        throw new InvalidFileTypeException("文件格式不支持，请上传常见的图片文件");
    }
}
