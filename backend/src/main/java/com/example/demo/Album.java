package com.example.demo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "albums")
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "相册名称不能为空")
    private String name;

    private String description;

    private Long coverPhotoId;

    private LocalDateTime createdAt;

    @ManyToMany
    @JoinTable(name = "photo_albums",
        joinColumns = @JoinColumn(name = "album_id"),
        inverseJoinColumns = @JoinColumn(name = "photo_id"))
    @JsonIgnore
    private Set<Photo> photos = new HashSet<>();

    public Album() {}

    public Album(String name) {
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCoverPhotoId() { return coverPhotoId; }
    public void setCoverPhotoId(Long coverPhotoId) { this.coverPhotoId = coverPhotoId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Set<Photo> getPhotos() { return photos; }
    public void setPhotos(Set<Photo> photos) { this.photos = photos; }

    @com.fasterxml.jackson.annotation.JsonProperty
    public int getPhotoCount() { return photos != null ? photos.size() : 0; }
}
