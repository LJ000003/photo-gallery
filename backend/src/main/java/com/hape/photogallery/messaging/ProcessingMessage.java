package com.hape.photogallery.messaging;

import java.io.Serializable;

/**
 * RabbitMQ 消息体，包含图片处理所需的全部参数。
 * 不含图片二进制数据（只传路径），消息体 < 1KB。
 */
public class ProcessingMessage implements Serializable {

    private Long photoId;
    private String targetPath;   // relative path, e.g. "2024/08/uuid_name.jpg"
    private String dateDir;      // "2024/08"
    private String baseName;     // "uuid_name.jpg"
    private String watermark;    // nullable

    public ProcessingMessage() {}

    public ProcessingMessage(Long photoId, String targetPath, String dateDir,
                             String baseName, String watermark) {
        this.photoId = photoId;
        this.targetPath = targetPath;
        this.dateDir = dateDir;
        this.baseName = baseName;
        this.watermark = watermark;
    }

    public Long getPhotoId() { return photoId; }
    public void setPhotoId(Long photoId) { this.photoId = photoId; }

    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }

    public String getDateDir() { return dateDir; }
    public void setDateDir(String dateDir) { this.dateDir = dateDir; }

    public String getBaseName() { return baseName; }
    public void setBaseName(String baseName) { this.baseName = baseName; }

    public String getWatermark() { return watermark; }
    public void setWatermark(String watermark) { this.watermark = watermark; }
}
