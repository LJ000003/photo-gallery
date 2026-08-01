package com.hape.photogallery.config;

import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.PhotoResponse;
import com.hape.photogallery.dto.TimelineItem;
import com.hape.photogallery.entity.Category;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Redis 缓存序列化往返测试：缓存值写入 Redis 后，再次读取必须能反序列化。
 * 覆盖 prod（spring.cache.type=redis）下实际缓存的几种形态：
 * List&lt;Category&gt;、Page&lt;PhotoResponse&gt;（含排序 Pageable、unmodifiable content）、
 * Page&lt;TimelineItem&gt;、List&lt;MapItem&gt;。
 */
class RedisConfigTest {

    private final GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(RedisConfig.redisObjectMapper());

    @Test
    void categoryListRoundTrips() {
        List<Category> categories = new ArrayList<>();
        Category scenery = new Category("风景");
        scenery.setId(1L);
        Category portrait = new Category("人像");
        portrait.setId(2L);
        categories.add(scenery);
        categories.add(portrait);

        List<Category> result = roundTrip(categories);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("风景", result.get(0).getName());
        assertEquals(2L, result.get(1).getId());
        assertEquals("人像", result.get(1).getName());
    }

    @Test
    void pageOfPhotoResponsesRoundTrips() {
        List<PhotoResponse> content = new ArrayList<>();
        content.add(photoResponse(1L, "日落", LocalDateTime.of(2026, 7, 1, 18, 30)));
        content.add(photoResponse(2L, "星空", LocalDateTime.of(2026, 7, 2, 23, 0)));

        // 模拟 PhotoService.listAllResponses()：PageImpl + 排序 Pageable + unmodifiable content
        Page<PhotoResponse> page = new PageImpl<>(
                Collections.unmodifiableList(content),
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"))),
                42);

        Page<?> result = roundTrip(page);

        assertNotNull(result);
        assertInstanceOf(PageImpl.class, result);
        assertEquals(42, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        assertEquals(0, result.getPageable().getPageNumber());
        assertEquals(20, result.getPageable().getPageSize());
        assertEquals("createdAt", result.getPageable().getSort().getOrderFor("createdAt").getProperty());
        PhotoResponse first = (PhotoResponse) result.getContent().get(0);
        assertEquals(1L, first.getId());
        assertEquals("日落", first.getName());
        assertEquals(LocalDateTime.of(2026, 7, 1, 18, 30), first.getCreatedAt());
    }

    @Test
    void timelinePageRoundTrips() {
        List<TimelineItem> items = new ArrayList<>();
        items.add(timelineItem(10L, 100L, LocalDateTime.of(2026, 7, 1, 12, 0)));

        Page<TimelineItem> page = new PageImpl<>(items, PageRequest.of(0, 50), 1);

        Page<?> result = roundTrip(page);

        assertEquals(1, result.getTotalElements());
        TimelineItem first = (TimelineItem) result.getContent().get(0);
        assertEquals(100L, first.getPhotoId());
        assertEquals(LocalDateTime.of(2026, 7, 1, 12, 0), first.getDateTaken());
    }

    @Test
    void mapItemListRoundTrips() {
        List<MapItem> items = new ArrayList<>();
        items.add(mapItem(200L, 31.23, 121.47));

        List<MapItem> result = roundTrip(items);

        assertEquals(1, result.size());
        assertEquals(200L, result.get(0).getPhotoId());
        assertEquals(31.23, result.get(0).getLatitude(), 1e-9);
        assertEquals(121.47, result.get(0).getLongitude(), 1e-9);
    }

    // ── helpers ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> T roundTrip(Object value) {
        byte[] bytes = serializer.serialize(value);
        return (T) serializer.deserialize(bytes);
    }

    private static PhotoResponse photoResponse(Long id, String name, LocalDateTime createdAt) {
        PhotoResponse r = new PhotoResponse();
        setField(r, "id", id);
        setField(r, "name", name);
        setField(r, "createdAt", createdAt);
        return r;
    }

    private static TimelineItem timelineItem(Long id, Long photoId, LocalDateTime dateTaken) {
        TimelineItem item = new TimelineItem();
        setField(item, "id", id);
        setField(item, "photoId", photoId);
        setField(item, "dateTaken", dateTaken);
        return item;
    }

    private static MapItem mapItem(Long photoId, double latitude, double longitude) {
        MapItem item = new MapItem();
        setField(item, "photoId", photoId);
        setField(item, "latitude", latitude);
        setField(item, "longitude", longitude);
        return item;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法构造测试对象字段 " + name, e);
        }
    }
}
