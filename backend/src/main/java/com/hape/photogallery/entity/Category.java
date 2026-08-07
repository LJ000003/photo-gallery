package com.hape.photogallery.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
@org.hibernate.annotations.BatchSize(size = 20) // 批量初始化 Photo.category 懒加载代理（替代 EAGER 的逐张查询）
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "category")
    @JsonIgnore
    private List<Photo> photos = new ArrayList<>();

    public Category() {}

    public Category(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Photo> getPhotos() { return photos; }
    public void setPhotos(List<Photo> photos) { this.photos = photos; }
}
