package com.hape.photogallery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class AlbumRequest {

    @NotBlank(message = "相册名称不能为空")
    @Size(max = 100, message = "相册名称不能超过 100 个字符")
    private String name;

    @Size(max = 500, message = "相册描述不能超过 500 个字符")
    private String description;

    private List<Long> photoIds;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Long> getPhotoIds() { return photoIds; }
    public void setPhotoIds(List<Long> photoIds) { this.photoIds = photoIds; }
}
