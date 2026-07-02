package com.example.demo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exif_data")
public class ExifData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", nullable = false, unique = true)
    @JsonIgnore
    private Photo photo;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Photo getPhoto() { return photo; }
    public void setPhoto(Photo photo) { this.photo = photo; }

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

    @com.fasterxml.jackson.annotation.JsonProperty
    public Long getPhotoId() { return photo != null ? photo.getId() : null; }

    @com.fasterxml.jackson.annotation.JsonProperty
    public String getPhotoName() { return photo != null ? photo.getName() : null; }

    @com.fasterxml.jackson.annotation.JsonProperty
    public String getPhotoThumbnail() { return photo != null ? "/api/photos/" + photo.getId() + "/thumbnail" : null; }
}
