package com.hape.photogallery.dto;

import com.hape.photogallery.entity.ExifData;

import java.time.LocalDateTime;

public class TimelineItem {

    private Long id;
    private Long photoId;
    private String photoName;
    private String photoThumbnail;
    private LocalDateTime dateTaken;
    private String cameraModel;

    public static TimelineItem from(ExifData exif) {
        TimelineItem item = new TimelineItem();
        item.id = exif.getId();
        item.photoId = exif.getPhotoId();
        item.photoName = exif.getPhotoName();
        item.photoThumbnail = exif.getPhotoThumbnail();
        item.dateTaken = exif.getDateTaken();
        item.cameraModel = exif.getCameraModel();
        return item;
    }

    public Long getId() { return id; }
    public Long getPhotoId() { return photoId; }
    public String getPhotoName() { return photoName; }
    public String getPhotoThumbnail() { return photoThumbnail; }
    public LocalDateTime getDateTaken() { return dateTaken; }
    public String getCameraModel() { return cameraModel; }
}
