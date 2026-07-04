package com.hape.photogallery;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class CategoryService {

    private final CategoryRepository catRepo;

    public CategoryService(CategoryRepository catRepo) {
        this.catRepo = catRepo;
    }

    public List<Category> listAll() {
        return catRepo.findAll();
    }

    public Category create(String name) {
        return catRepo.findByName(name).orElseGet(() -> catRepo.save(new Category(name)));
    }

    public void delete(Long id) {
        catRepo.deleteById(id);
    }

    public Category update(Long id, String name) {
        Category cat = catRepo.findById(id).orElseThrow(() -> new RuntimeException("分类不存在"));
        if (name != null) cat.setName(name);
        return catRepo.save(cat);
    }
}
