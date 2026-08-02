package com.hape.photogallery.dto;

/**
 * 备份导出请求：全部字段可选，null 表示不限制。
 * dateFrom/dateTo 为 "yyyy-MM-dd" 格式（服务端解析，非法值返回 400）。
 * albumId=0 表示「未分配任何相册」的照片（与 /photos 列表语义一致）。
 */
public class BackupExportRequest {

    private Long albumId;
    private Long categoryId;
    private String dateFrom;
    private String dateTo;

    public Long getAlbumId() { return albumId; }
    public void setAlbumId(Long albumId) { this.albumId = albumId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getDateFrom() { return dateFrom; }
    public void setDateFrom(String dateFrom) { this.dateFrom = dateFrom; }
    public String getDateTo() { return dateTo; }
    public void setDateTo(String dateTo) { this.dateTo = dateTo; }
}
