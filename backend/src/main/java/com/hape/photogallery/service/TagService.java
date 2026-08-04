package com.hape.photogallery.service;

import java.util.List;

import com.hape.photogallery.entity.Tag;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.repository.TagRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

    // evict 对照表（聚合根 → 依赖缓存）：
    //   create 不改变已有照片 → 只失效 tags/stats
    //   delete/update 会改变 PhotoResponse 内嵌的 tags → 必须连带失效 photos（最长 30s 显示旧标签）
    private final TagRepository tagRepo;

    public TagService(TagRepository tagRepo) {
        this.tagRepo = tagRepo;
    }

    @Cacheable("tags")
    public List<Tag> listAll() {
        return tagRepo.findAll();
    }

    @Transactional
    @CacheEvict(value = {"tags", "stats"}, allEntries = true)
    public Tag create(String name, String color) {
        return tagRepo.findByName(name).orElseGet(() -> tagRepo.save(new Tag(name, color)));
    }

    @Transactional
    @CacheEvict(value = {"tags", "stats", "photos"}, allEntries = true)
    public void delete(Long id) {
        tagRepo.deleteById(id);
    }

    @Transactional
    @CacheEvict(value = {"tags", "stats", "photos"}, allEntries = true)
    public Tag update(Long id, String name, String color) {
        Tag tag = tagRepo.findById(id).orElseThrow(() -> new BusinessException(404, "标签不存在"));
        if (name != null) tag.setName(name);
        if (color != null) tag.setColor(color);
        return tagRepo.save(tag);
    }
}
