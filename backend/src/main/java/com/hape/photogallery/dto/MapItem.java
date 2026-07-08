package com.hape.photogallery.dto;

import com.hape.photogallery.entity.ExifData;

public class MapItem {

    private Long photoId;
    private String photoName;
    private String photoThumbnail;
    private double latitude;
    private double longitude;

    public static MapItem from(ExifData exif) {
        MapItem item = new MapItem();
        item.photoId = exif.getPhotoId();
        item.photoName = exif.getPhotoName();
        item.photoThumbnail = exif.getPhotoThumbnail();
        item.latitude = exif.getLatitude();
        item.longitude = exif.getLongitude();
        return item;
    }

    public Long getPhotoId() { return photoId; }
    public String getPhotoName() { return photoName; }
    public String getPhotoThumbnail() { return photoThumbnail; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
