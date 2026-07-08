package com.hape.photogallery.dto;

import com.hape.photogallery.entity.Album;
import com.hape.photogallery.entity.Category;
import com.hape.photogallery.entity.Photo;
import com.hape.photogallery.entity.Tag;

import java.time.LocalDateTime;
import java.util.Set;

public class PhotoResponse {

    private Long id;
    private String name;
    private String description;
    private Long fileSize;
    private Category category;
    private Set<Tag> tags;
    private Set<Album> albums;
    private LocalDateTime createdAt;

    public static PhotoResponse from(Photo photo) {
        PhotoResponse r = new PhotoResponse();
        r.id = photo.getId();
        r.name = photo.getName();
        r.description = photo.getDescription();
        r.fileSize = photo.getFileSize();
        r.category = photo.getCategory();
        r.tags = photo.getTags();
        r.albums = photo.getAlbums();
        r.createdAt = photo.getCreatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getFileSize() { return fileSize; }
    public Category getCategory() { return category; }
    public Set<Tag> getTags() { return tags; }
    public Set<Album> getAlbums() { return albums; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
