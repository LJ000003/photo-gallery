package com.hape.photogallery.service;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    Path getUploadDir();
    Path resolveSafe(String relativePath);
    void store(MultipartFile file, Path target) throws IOException;

    /** 删除文件；失败（IO 错误/权限）返回 false 而非吞异常——调用方据此记录删除失败清单 */
    boolean deleteFile(String relativePath);
    void createDirectories(Path dir) throws IOException;
}
