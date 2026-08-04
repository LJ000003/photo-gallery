package com.hape.photogallery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "照片更新请求（null = 不修改；categoryId=0 清除分类）")
public class PhotoUpdateRequest {

    @Schema(description = "照片名称")
    @NotBlank(message = "照片名称不能为空")
    private String name;

    @Schema(description = "描述")
    @Size(max = 500, message = "描述不能超过500字")
    private String description;

    @Schema(description = "标签 ID 列表（传数组 = 整体替换）")
    private List<Long> tagIds;

    @Schema(description = "分类 ID：null=不修改 / 0=清除分类 / >0=设为指定分类")
    private Long categoryId;

    @Schema(description = "相册 ID 列表（null=不修改）")
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
