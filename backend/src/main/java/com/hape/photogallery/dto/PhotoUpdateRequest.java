package com.hape.photogallery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class PhotoUpdateRequest {

    @NotBlank(message = "照片名称不能为空")
    private String name;

    @Size(max = 500, message = "描述不能超过500字")
    private String description;

    private List<Long> tagIds;
    private Long categoryId;
    private List<Long> albumIds;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Long> getTagIds() { return tagIds; }
    public void setTagIds(List<Long> tagIds) { this.tagIds = tagIds; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public List<Long> getAlbumIds() { return albumIds; }
    public void setAlbumIds(List<Long> albumIds) { this.albumIds = albumIds; }
}
