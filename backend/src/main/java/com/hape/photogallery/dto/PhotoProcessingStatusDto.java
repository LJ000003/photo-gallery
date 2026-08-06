package com.hape.photogallery.dto;

import com.hape.photogallery.entity.Photo;

/**
 * 处理状态轻量 DTO（轮询专用）：只含 id + 状态 + 错误信息。
 * 独立于 PhotoResponse——轮询每 3s 一次，全量 DTO（exif/tags/albums）纯属带宽浪费；
 * 且本端点不加 @Cacheable（30s 缓存会废掉轮询的实时性）。
 */
public class PhotoProcessingStatusDto {

    private Long id;
    private String processingStatus;
    private String errorMessage;

    public static PhotoProcessingStatusDto from(Photo photo) {
        PhotoProcessingStatusDto dto = new PhotoProcessingStatusDto();
        dto.id = photo.getId();
        dto.processingStatus = photo.getProcessingStatus() != null ? photo.getProcessingStatus().name() : null;
        dto.errorMessage = photo.getErrorMessage();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
