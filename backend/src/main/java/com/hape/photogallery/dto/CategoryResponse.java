package com.hape.photogallery.dto;

import com.hape.photogallery.entity.Category;

/**
 * 分类响应 DTO（替代实体序列化，与 AlbumResponse 同策略）。
 * Photo.category 是 @ManyToOne LAZY 代理——直接塞实体进 PhotoResponse 时，
 * 未初始化代理（Category$HibernateProxy）会被 GenericJackson2JsonRedisSerializer 序列化失败（500，
 * prod Redis 缓存实测复现）；from() 内访问 getName() 同时触发代理初始化。
 */
public class CategoryResponse {

    private Long id;
    private String name;

    public static CategoryResponse from(Category c) {
        CategoryResponse r = new CategoryResponse();
        r.id = c.getId();
        r.name = c.getName();
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
