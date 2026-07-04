package com.hape.photogallery;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
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
        Tag tag = tagService.create(body.get("name"), body.get("color"));
        return ApiResponse.success(tag);
    }

    @DeleteMapping("/tags/{id}")
    public ApiResponse<String> deleteTag(@PathVariable Long id) {
        tagService.delete(id);
        return ApiResponse.success("删除成功");
    }

    @PutMapping("/tags/{id}")
    public ApiResponse<Tag> updateTag(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.success(tagService.update(id, body.get("name"), body.get("color")));
    }
}
