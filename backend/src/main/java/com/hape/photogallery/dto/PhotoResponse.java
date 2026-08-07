package com.hape.photogallery.dto;

import com.hape.photogallery.entity.Photo;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class PhotoResponse {

    private Long id;
    private String name;
    private String description;
    private Long fileSize;
    /** DTO 而非 Category 实体——懒加载代理（Category$HibernateProxy）进 Redis 序列化必 500（prod 实测） */
    private CategoryResponse category;
    /** DTO 而非 Tag 实体——与 category/exifData 的 DTO 化策略统一（JSON 契约不变：id/name/color） */
    private Set<TagResponse> tags;
    /** 轻量 DTO（仅 id/name）——避免序列化 Album 实体触发 getPhotoCount() 的整集合懒加载 N+1 */
    private Set<AlbumResponse> albums;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    private String processingStatus;
    private String errorMessage;
    /** DTO 而非 ExifData 实体——@OneToOne LAZY 代理进 Redis 序列化必 500（prod 实测） */
    private ExifDataResponse exifData;
    /** 图片 URL 短时签名（HMAC 时间桶），仅管理员上下文签发；分享上下文剥离 */
    private String mediaToken;

    public static PhotoResponse from(Photo photo) {
        PhotoResponse r = new PhotoResponse();
        r.id = photo.getId();
        r.name = photo.getName();
        r.description = photo.getDescription();
        r.fileSize = photo.getFileSize();
        // from 内访问关联属性（name/color/相机字段）同时触发懒代理初始化，返回后即实对象
        r.category = photo.getCategory() != null ? CategoryResponse.from(photo.getCategory()) : null;
        r.tags = photo.getTags() != null
                ? photo.getTags().stream().map(TagResponse::from).collect(Collectors.toSet())
                : new HashSet<>();
        r.albums = photo.getAlbums() != null
                ? photo.getAlbums().stream().map(AlbumResponse::summary).collect(Collectors.toSet())
                : new HashSet<>();
        r.createdAt = photo.getCreatedAt();
        r.deletedAt = photo.getDeletedAt();
        r.processingStatus = photo.getProcessingStatus().name();
        r.errorMessage = photo.getErrorMessage();
        r.exifData = photo.getExifData() != null ? ExifDataResponse.from(photo.getExifData()) : null;
        return r;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getFileSize() { return fileSize; }
    public CategoryResponse getCategory() { return category; }
    public Set<TagResponse> getTags() { return tags; }
    public Set<AlbumResponse> getAlbums() { return albums; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public String getProcessingStatus() { return processingStatus; }
    public String getErrorMessage() { return errorMessage; }
    public ExifDataResponse getExifData() { return exifData; }
    public String getMediaToken() { return mediaToken; }
    public void setMediaToken(String mediaToken) { this.mediaToken = mediaToken; }
}
