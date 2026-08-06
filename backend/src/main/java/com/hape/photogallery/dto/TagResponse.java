package com.hape.photogallery.dto;

import com.hape.photogallery.entity.Tag;

/** 标签响应 DTO（替代实体序列化——Photo.tags 集合元素直接序列化虽可行，但与 category/exifData 的
 *  DTO 化策略统一，避免实体字段扩散进缓存与响应契约） */
public class TagResponse {

    private Long id;
    private String name;
    private String color;

    public static TagResponse from(Tag t) {
        TagResponse r = new TagResponse();
        r.id = t.getId();
        r.name = t.getName();
        r.color = t.getColor();
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
