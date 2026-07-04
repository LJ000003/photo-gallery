package com.hape.photogallery;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlbumService {

    private final AlbumRepository albumRepo;
    private final PhotoRepository photoRepo;

    public AlbumService(AlbumRepository albumRepo, PhotoRepository photoRepo) {
        this.albumRepo = albumRepo;
        this.photoRepo = photoRepo;
    }

    public List<Album> listAll() {
        return albumRepo.findAll();
    }

    @CacheEvict(value = "photos", allEntries = true)
    @Transactional
    public Album create(String name, String description, List<Long> photoIds) {
        Album a = new Album(name);
        a.setDescription(description);
        a = albumRepo.save(a);
        if (photoIds != null && !photoIds.isEmpty()) {
            Set<Photo> photos = new HashSet<>(photoRepo.findAllById(photoIds));
            a.setPhotos(photos);
            for (Photo p : photos) {
                p.getAlbums().add(a);
                photoRepo.save(p);
            }
            a.setCoverPhotoId(photoIds.get(0));
            albumRepo.save(a);
        }
        return a;
    }

    @CacheEvict(value = "photos", allEntries = true)
    @Transactional
    public Album update(Long id, String name, String description, List<Long> photoIds) {
        Album a = albumRepo.findById(id).orElseThrow(() -> new RuntimeException("相册不存在"));
        if (name != null) a.setName(name);
        if (description != null) a.setDescription(description);
        if (photoIds != null) {
            for (Photo p : new HashSet<>(a.getPhotos())) {
                p.getAlbums().remove(a);
                photoRepo.save(p);
            }
            a.getPhotos().clear();
            Set<Photo> photos = new HashSet<>(photoRepo.findAllById(photoIds));
            for (Photo p : photos) {
                p.getAlbums().add(a);
                photoRepo.save(p);
            }
            a.setPhotos(photos);
            if (!photos.isEmpty()) {
                a.setCoverPhotoId(photos.iterator().next().getId());
            } else {
                a.setCoverPhotoId(null);
            }
        }
        return albumRepo.save(a);
    }

    @Transactional
    @CacheEvict(value = "photos", allEntries = true)
    public void delete(Long id) {
        Album a = albumRepo.findById(id).orElseThrow(() -> new RuntimeException("相册不存在"));
        for (Photo p : new HashSet<>(a.getPhotos())) {
            p.getAlbums().remove(a);
            photoRepo.save(p);
        }
        a.getPhotos().clear();
        albumRepo.delete(a);
    }

    public Page<Photo> listPhotos(Long albumId, Pageable pageable) {
        return photoRepo.findByAlbumId(albumId, pageable);
    }

    public Page<Photo> listUnassigned(Pageable pageable) {
        return photoRepo.findUnassigned(pageable);
    }

    @Transactional
    @CacheEvict(value = "photos", allEntries = true)
    public void addPhotos(Long albumId, List<Long> photoIds) {
        Album a = albumRepo.findById(albumId).orElseThrow(() -> new RuntimeException("相册不存在"));
        for (Long pid : photoIds) {
            Photo p = photoRepo.findById(pid).orElse(null);
            if (p != null) {
                a.getPhotos().add(p);
                p.getAlbums().add(a);
                photoRepo.save(p);
            }
        }
        if (a.getCoverPhotoId() == null && !photoIds.isEmpty()) {
            a.setCoverPhotoId(photoIds.get(0));
        }
        albumRepo.save(a);
    }

    @Transactional
    @CacheEvict(value = "photos", allEntries = true)
    public void removePhotos(Long albumId, List<Long> photoIds) {
        Album a = albumRepo.findById(albumId).orElseThrow(() -> new RuntimeException("相册不存在"));
        for (Long pid : photoIds) {
            Photo p = photoRepo.findById(pid).orElse(null);
            if (p != null) {
                a.getPhotos().remove(p);
                p.getAlbums().remove(a);
                photoRepo.save(p);
            }
        }
        if (photoIds.contains(a.getCoverPhotoId())) {
            a.setCoverPhotoId(a.getPhotos().isEmpty() ? null : a.getPhotos().iterator().next().getId());
        }
        albumRepo.save(a);
    }

    @Transactional
    public void syncPhotoAlbums(Photo photo, List<Long> albumIds) {
        for (Album a : new HashSet<>(photo.getAlbums())) {
            if (!albumIds.contains(a.getId())) {
                a.getPhotos().remove(photo);
                photo.getAlbums().remove(a);
                albumRepo.save(a);
            }
        }
        for (Long aid : albumIds) {
            if (photo.getAlbums().stream().noneMatch(a -> a.getId().equals(aid))) {
                Album a = albumRepo.findById(aid).orElse(null);
                if (a != null) {
                    a.getPhotos().add(photo);
                    photo.getAlbums().add(a);
                    albumRepo.save(a);
                }
            }
        }
    }
}
