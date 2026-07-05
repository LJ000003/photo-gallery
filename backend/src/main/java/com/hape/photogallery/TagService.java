package com.hape.photogallery;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
public class TagService {

    private final TagRepository tagRepo;

    public TagService(TagRepository tagRepo) {
        this.tagRepo = tagRepo;
    }

    public List<Tag> listAll() {
        return tagRepo.findAll();
    }

    @CacheEvict(value = "photos", allEntries = true)
    public Tag create(String name, String color) {
        return tagRepo.findByName(name).orElseGet(() -> tagRepo.save(new Tag(name, color)));
    }

    @CacheEvict(value = "photos", allEntries = true)
    public void delete(Long id) {
        tagRepo.deleteById(id);
    }

    @CacheEvict(value = "photos", allEntries = true)
    public Tag update(Long id, String name, String color) {
        Tag tag = tagRepo.findById(id).orElseThrow(() -> new RuntimeException("标签不存在"));
        if (name != null) tag.setName(name);
        if (color != null) tag.setColor(color);
        return tagRepo.save(tag);
    }
}
