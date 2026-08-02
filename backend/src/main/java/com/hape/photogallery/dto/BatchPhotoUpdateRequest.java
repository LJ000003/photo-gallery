package com.hape.photogallery.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量编辑请求：对多张照片添加/移除标签、相册，设置或清除分类。
 * 空列表表示不操作对应项；categoryOp=NONE 表示不修改分类。
 */
public class BatchPhotoUpdateRequest {

    public enum CategoryOp { NONE, SET, CLEAR }

    @NotEmpty(message = "请至少选择一张照片")
    @Size(max = 50, message = "单次最多处理 50 张照片")
    private List<Long> photoIds;

    private List<Long> addTagIds = List.of();
    private List<Long> removeTagIds = List.of();
    private List<Long> addAlbumIds = List.of();
    private List<Long> removeAlbumIds = List.of();

    private CategoryOp categoryOp = CategoryOp.NONE;
    private Long categoryId;

    @AssertTrue(message = "设为分类时必须指定分类")
    public boolean isCategoryValid() {
        return categoryOp != CategoryOp.SET || categoryId != null;
    }

    public List<Long> getPhotoIds() { return photoIds; }
    public void setPhotoIds(List<Long> photoIds) { this.photoIds = photoIds; }
    public List<Long> getAddTagIds() { return addTagIds; }
    public void setAddTagIds(List<Long> addTagIds) { this.addTagIds = addTagIds; }
    public List<Long> getRemoveTagIds() { return removeTagIds; }
    public void setRemoveTagIds(List<Long> removeTagIds) { this.removeTagIds = removeTagIds; }
    public List<Long> getAddAlbumIds() { return addAlbumIds; }
    public void setAddAlbumIds(List<Long> addAlbumIds) { this.addAlbumIds = addAlbumIds; }
    public List<Long> getRemoveAlbumIds() { return removeAlbumIds; }
    public void setRemoveAlbumIds(List<Long> removeAlbumIds) { this.removeAlbumIds = removeAlbumIds; }
    public CategoryOp getCategoryOp() { return categoryOp; }
    public void setCategoryOp(CategoryOp categoryOp) { this.categoryOp = categoryOp; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
}
