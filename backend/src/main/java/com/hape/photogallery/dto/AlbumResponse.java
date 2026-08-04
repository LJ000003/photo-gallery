package com.hape.photogallery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 相册响应 DTO（P4-#38：替代 JPA 实体序列化）。
 * - photoCount 由分组计数查询填充，不再触发 Album.getPhotoCount() 的整集合懒加载（N+1）；
 * - mediaToken 为封面图短时签名（HMAC 时间桶），序列化进响应但不落库，分享上下文剥离；
 * - deletedAt 可空，回收站列表需要（与前端 types/album.ts 字段对齐）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlbumResponse {

    private Long id;
    private String name;
    private String description;
    private Long coverPhotoId;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    private int photoCount;
    private String mediaToken;

    public AlbumResponse() {}

    public static AlbumResponse from(com.hape.photogallery.entity.Album a, int photoCount) {
        AlbumResponse r = new AlbumResponse();
        r.id = a.getId();
        r.name = a.getName();
        r.description = a.getDescription();
        r.coverPhotoId = a.getCoverPhotoId();
        r.createdAt = a.getCreatedAt();
        r.deletedAt = a.getDeletedAt();
        r.photoCount = photoCount;
        return r;
    }

    /** 嵌套进 PhotoResponse 的轻量视图：仅 id/name（前端只消费 a.id） */
    public static AlbumResponse summary(com.hape.photogallery.entity.Album a) {
        AlbumResponse r = new AlbumResponse();
        r.id = a.getId();
        r.name = a.getName();
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCoverPhotoId() { return coverPhotoId; }
    public void setCoverPhotoId(Long coverPhotoId) { this.coverPhotoId = coverPhotoId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public int getPhotoCount() { return photoCount; }
    public void setPhotoCount(int photoCount) { this.photoCount = photoCount; }
    public String getMediaToken() { return mediaToken; }
    public void setMediaToken(String mediaToken) { this.mediaToken = mediaToken; }
}
