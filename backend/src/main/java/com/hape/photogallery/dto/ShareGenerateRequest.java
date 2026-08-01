package com.hape.photogallery.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class ShareGenerateRequest {

    @NotEmpty(message = "请选择至少一张照片")
    private List<Long> photoIds;

    private String permission = "view";

    @Min(value = 1, message = "过期天数必须大于 0")
    private int expireDays = 7;

    public List<Long> getPhotoIds() { return photoIds; }
    public void setPhotoIds(List<Long> photoIds) { this.photoIds = photoIds; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public int getExpireDays() { return expireDays; }
    public void setExpireDays(int expireDays) { this.expireDays = expireDays; }
}
