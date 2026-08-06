package com.hape.photogallery.dto;

import com.hape.photogallery.entity.Category;
import com.hape.photogallery.entity.ExifData;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.Tag;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class PhotoResponse {

    private Long id;
    private String name;
    private String description;
    private Long fileSize;
    private Category category;
    private Set<Tag> tags;
    /** 轻量 DTO（仅 id/name）——避免序列化 Album 实体触发 getPhotoCount() 的整集合懒加载 N+1 */
    private Set<AlbumResponse> albums;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    private String processingStatus;
    private String errorMessage;
    private ExifData exifData;
    /** 图片 URL 短时签名（HMAC 时间桶），仅管理员上下文签发；分享上下文剥离 */
    private String mediaToken;

    public static PhotoResponse from(Photo photo) {
        PhotoResponse r = new PhotoResponse();
        r.id = photo.getId();
        r.name = photo.getName();
        r.description = photo.getDescription();
        r.fileSize = photo.getFileSize();
        r.category = photo.getCategory();
        r.tags = photo.getTags() != null ? new HashSet<>(photo.getTags()) : new HashSet<>();
        r.albums = photo.getAlbums() != null
                ? photo.getAlbums().stream().map(AlbumResponse::summary).collect(java.util.stream.Collectors.toSet())
                : new HashSet<>();
        r.createdAt = photo.getCreatedAt();
        r.deletedAt = photo.getDeletedAt();
        r.processingStatus = photo.getProcessingStatus().name();
        r.errorMessage = photo.getErrorMessage();
        r.exifData = photo.getExifData();
        return r;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getFileSize() { return fileSize; }
    public Category getCategory() { return category; }
    public Set<Tag> getTags() { return tags; }
    public Set<AlbumResponse> getAlbums() { return albums; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public String getProcessingStatus() { return processingStatus; }
    public String getErrorMessage() { return errorMessage; }
    public ExifData getExifData() { return exifData; }
    public String getMediaToken() { return mediaToken; }
    public void setMediaToken(String mediaToken) { this.mediaToken = mediaToken; }
}
