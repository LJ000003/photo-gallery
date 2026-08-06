package com.hape.photogallery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

@Schema(description = "分享链接生成请求")
public class ShareGenerateRequest {

    @Schema(description = "分享的照片 ID 白名单（至少一张）")
    @NotEmpty(message = "请选择至少一张照片")
    private List<Long> photoIds;

    /** 分享权限仅支持 view/download，任意字符串会被拒绝（permission claim 随 JWT 签发，不可放任非法值）；
     *  @Pattern 不拦 null（Bean Validation 语义），需 @NotNull 兜住显式传 null 的请求（曾 NPE → 500 / null 落库） */
    @Schema(description = "权限范围：view=仅查看 / download=可下载原图", example = "view")
    @NotNull(message = "分享权限不能为空")
    @Pattern(regexp = "view|download", message = "分享权限只能是 view 或 download")
    private String permission = "view";

    @Schema(description = "过期天数", example = "7")
    @Min(value = 1, message = "过期天数必须大于 0")
    private int expireDays = 7;

    public List<Long> getPhotoIds() { return photoIds; }
    public void setPhotoIds(List<Long> photoIds) { this.photoIds = photoIds; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public int getExpireDays() { return expireDays; }
    public void setExpireDays(int expireDays) { this.expireDays = expireDays; }
}
