package com.hape.photogallery.controller;

import java.util.List;
import java.util.Map;

import com.hape.photogallery.ApiResponse;
import com.hape.photogallery.entity.Tag;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.service.TagService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/tags")
    public ApiResponse<List<Tag>> listTags() {
        return ApiResponse.success(tagService.listAll());
    }

    @PostMapping("/tags")
    public ApiResponse<Tag> createTag(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "标签名称不能为空");
        }
        Tag tag = tagService.create(name, body.get("color"));
        return ApiResponse.success(tag);
    }

    @DeleteMapping("/tags/{id}")
    public ApiResponse<String> deleteTag(@PathVariable Long id) {
        tagService.delete(id);
        return ApiResponse.success("删除成功");
    }

    @PutMapping("/tags/{id}")
    public ApiResponse<Tag> updateTag(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name != null && name.isBlank()) {
            throw new BusinessException(400, "标签名称不能为空字符串");
        }
        return ApiResponse.success(tagService.update(id, name, body.get("color")));
    }
}
