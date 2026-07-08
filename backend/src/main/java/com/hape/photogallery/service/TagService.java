package com.hape.photogallery.service;

import java.util.List;

import com.hape.photogallery.entity.Tag;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.TagRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class TagService {

    private final TagRepository tagRepo;

    public TagService(TagRepository tagRepo) {
        this.tagRepo = tagRepo;
    }

    @Cacheable("tags")
    public List<Tag> listAll() {
        return tagRepo.findAll();
    }

    @CacheEvict(value = "tags", allEntries = true)
    public Tag create(String name, String color) {
        return tagRepo.findByName(name).orElseGet(() -> tagRepo.save(new Tag(name, color)));
    }

    @CacheEvict(value = "tags", allEntries = true)
    public void delete(Long id) {
        tagRepo.deleteById(id);
    }

    @CacheEvict(value = "tags", allEntries = true)
    public Tag update(Long id, String name, String color) {
        Tag tag = tagRepo.findById(id).orElseThrow(() -> new BusinessException(404, "标签不存在"));
        if (name != null) tag.setName(name);
        if (color != null) tag.setColor(color);
        return tagRepo.save(tag);
    }
}
