package com.hape.photogallery.dto;

import com.hape.photogallery.entity.ExifData;

import java.time.LocalDateTime;

/**
 * EXIF 响应 DTO（替代实体序列化——Photo.exifData 是 @OneToOne LAZY 代理，
 * 未初始化代理进 Redis 序列化会失败；from() 内访问标量字段同时触发代理初始化）。
 * 字段与 ExifData 实体的序列化输出完全一致（含 @JsonProperty 的 photoId/photoName/photoThumbnail），
 * 前端零改动。
 */
public class ExifDataResponse {

    private Long id;
    private LocalDateTime dateTaken;
    private String cameraModel;
    private String lensModel;
    private String focalLength;
    private String aperture;
    private String shutterSpeed;
    private Integer iso;
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Long photoId;
    private String photoName;
    private String photoThumbnail;

    public static ExifDataResponse from(ExifData e) {
        ExifDataResponse r = new ExifDataResponse();
        r.id = e.getId();
        r.dateTaken = e.getDateTaken();
        r.cameraModel = e.getCameraModel();
        r.lensModel = e.getLensModel();
        r.focalLength = e.getFocalLength();
        r.aperture = e.getAperture();
        r.shutterSpeed = e.getShutterSpeed();
        r.iso = e.getIso();
        r.latitude = e.getLatitude();
        r.longitude = e.getLongitude();
        r.altitude = e.getAltitude();
        r.photoId = e.getPhotoId();
        r.photoName = e.getPhotoName();
        r.photoThumbnail = e.getPhotoThumbnail();
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getDateTaken() { return dateTaken; }
    public void setDateTaken(LocalDateTime dateTaken) { this.dateTaken = dateTaken; }
    public String getCameraModel() { return cameraModel; }
    public void setCameraModel(String cameraModel) { this.cameraModel = cameraModel; }
    public String getLensModel() { return lensModel; }
    public void setLensModel(String lensModel) { this.lensModel = lensModel; }
    public String getFocalLength() { return focalLength; }
    public void setFocalLength(String focalLength) { this.focalLength = focalLength; }
    public String getAperture() { return aperture; }
    public void setAperture(String aperture) { this.aperture = aperture; }
    public String getShutterSpeed() { return shutterSpeed; }
    public void setShutterSpeed(String shutterSpeed) { this.shutterSpeed = shutterSpeed; }
    public Integer getIso() { return iso; }
    public void setIso(Integer iso) { this.iso = iso; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getAltitude() { return altitude; }
    public void setAltitude(Double altitude) { this.altitude = altitude; }
    public Long getPhotoId() { return photoId; }
    public void setPhotoId(Long photoId) { this.photoId = photoId; }
    public String getPhotoName() { return photoName; }
    public void setPhotoName(String photoName) { this.photoName = photoName; }
    public String getPhotoThumbnail() { return photoThumbnail; }
    public void setPhotoThumbnail(String photoThumbnail) { this.photoThumbnail = photoThumbnail; }
}
