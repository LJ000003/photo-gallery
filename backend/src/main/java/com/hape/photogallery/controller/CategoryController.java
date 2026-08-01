package com.hape.photogallery.controller;

import java.util.List;
import java.util.Map;

import com.hape.photogallery.ApiResponse;
import com.hape.photogallery.entity.Category;
import com.hape.photogallery.exception.BusinessException;
import com.hape.photogallery.service.CategoryService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<Category>> listCategories() {
        return ApiResponse.success(categoryService.listAll());
    }

    @PostMapping("/categories")
    public ApiResponse<Category> createCategory(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "分类名称不能为空");
        }
        Category cat = categoryService.create(name);
        return ApiResponse.success(cat);
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<String> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.success("删除成功");
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<Category> updateCategory(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name != null && name.isBlank()) {
            throw new BusinessException(400, "分类名称不能为空字符串");
        }
        return ApiResponse.success(categoryService.update(id, name));
    }
}
