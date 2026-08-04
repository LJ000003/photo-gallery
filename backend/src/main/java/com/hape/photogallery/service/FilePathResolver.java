package com.hape.photogallery.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.PhotoRepository;

/**
 * 存储路径解析（P4-#37：从 PhotoService 拆出）。
 * 收敛「实体 fileName → 磁盘 Path」的规则（缩略图/WebP 目录约定、越界防御），
 * 含 getByIdIncludeDeleted 的 404 语义——API 形状保持 getFilePath(Long id) 不变。
 */
@Component
public class FilePathResolver {

    private final PhotoRepository repo;
    private final StorageService storage;

    public FilePathResolver(PhotoRepository repo, StorageService storage) {
        this.repo = repo;
        this.storage = storage;
    }

    /** 存储路径拆分（如 "2024/01/uuid_file.jpg" → {dateDir, baseName}） */
    public record FilePathParts(String dateDir, String baseName) {}

    /**
     * 将存储路径拆为 {dateDir, baseName}。
     * 对不含 '/' 的文件名安全降级，防止 StringIndexOutOfBoundsException。
     */
    public FilePathParts parseFilePath(String fn) {
        int lastSlash = fn.lastIndexOf('/');
        if (lastSlash < 0) {
            return new FilePathParts("", fn);
        }
        return new FilePathParts(fn.substring(0, lastSlash), fn.substring(lastSlash + 1));
    }

    /** 查照片（含软删除），不存在抛 404——getFilePath 等路径查询的公共语义 */
    public Photo getByIdIncludeDeleted(Long id) {
        return repo.findById(id)
                .or(() -> repo.findDeletedById(id))
                .orElseThrow(() -> new BusinessException(404, "该照片不存在"));
    }

    public Path getFilePath(Long id) {
        Photo photo = getByIdIncludeDeleted(id);
        return storage.resolveSafe(photo.getFileName());
    }

    public Path getThumbnailPath(Long id) {
        return getThumbnailPath(id, 400);
    }

    public Path getThumbnailPath(Long id, int width) {
        Photo photo = getByIdIncludeDeleted(id);
        String fn = photo.getFileName();
        FilePathParts parts = parseFilePath(fn);

        Path uploadDir = storage.getUploadDir();
        Path thumbDir = width == 400
                ? uploadDir.resolve(parts.dateDir).resolve("thumbnails")
                : uploadDir.resolve(parts.dateDir).resolve("thumbnails").resolve(String.valueOf(width));
        Path thumb = storage.resolveSafe(uploadDir.relativize(thumbDir.resolve(parts.baseName)).toString());
        if (Files.exists(thumb)) return thumb;

        if (width != 400) {
            Path fallback = storage.resolveSafe(parts.dateDir + "/thumbnails/" + parts.baseName);
            if (Files.exists(fallback)) return fallback;
        }

        return storage.resolveSafe(fn);
    }

    public Path getWebpPath(Long id) {
        Photo photo = getByIdIncludeDeleted(id);
        String fn = photo.getFileName();
        FilePathParts parts = parseFilePath(fn);
        Path webp = storage.resolveSafe(parts.dateDir + "/webp/" + parts.baseName + ".webp");
        if (Files.exists(webp)) return webp;
        return getFilePath(id);
    }

    /** 删除照片全部磁盘产物（原图/缩略图×2/WebP） */
    public void deletePhotoFiles(Photo photo) {
        storage.deleteFile(photo.getFileName());
        FilePathParts parts = parseFilePath(photo.getFileName());
        storage.deleteFile(parts.dateDir + "/thumbnails/" + parts.baseName);
        storage.deleteFile(parts.dateDir + "/thumbnails/200/" + parts.baseName);
        storage.deleteFile(parts.dateDir + "/webp/" + parts.baseName + ".webp");
    }
}
