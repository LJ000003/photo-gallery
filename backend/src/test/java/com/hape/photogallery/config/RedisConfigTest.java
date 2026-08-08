package com.hape.photogallery.config;

import com.hape.photogallery.dto.MapItem;
import com.hape.photogallery.dto.StatsResponse;
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
    void emptyListRoundTrips() {
        // 空列表是真实场景：相册/标签/分类缓存无数据时回源写入，必须能再次读出
        List<Category> empty = new ArrayList<>();
        byte[] bytes = serializer.serialize(empty);
        // 空列表必须带类型包裹（["java.util.ArrayList",[]]），裸 [] 反序列化会抛
        // SerializationException: Unexpected token (END_ARRAY), expected VALUE_STRING
        String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("[\"java.util.ArrayList\",[]]", json);
        List<?> result = roundTrip(empty);
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void statsResponseWithToListFieldsRoundTrips() {
        // StatsService.getStats 的字段是 stream().toList()（ListN）——字段声明类型 List 非 final，
        // 序列化写运行时类型 id，非空/空均可读回（实测 pg:stats 往返 200）；
        // 与「根值」场景（@Cacheable 返回 ListN 空 → 裸 []）不同，字段场景安全。固化防回归。
        StatsResponse nonEmpty = new StatsResponse(2, 1000,
                java.util.stream.Stream.of(new StatsResponse.MonthlyTrend("2026-08", 2)).toList(),
                java.util.stream.Stream.of(new StatsResponse.TopTag("风景", "#fff", 2)).toList());
        StatsResponse empty = new StatsResponse(0, 0,
                java.util.stream.Stream.<StatsResponse.MonthlyTrend>of().toList(),
                java.util.stream.Stream.<StatsResponse.TopTag>of().toList());

        StatsResponse r1 = roundTrip(nonEmpty);
        assertEquals(2, r1.getTotalPhotos());
        assertEquals(1, r1.getMonthlyTrend().size());
        assertEquals("2026-08", r1.getMonthlyTrend().get(0).getMonth());
        assertEquals("风景", r1.getTopTags().get(0).getName());

        StatsResponse r2 = roundTrip(empty);
        assertEquals(0, r2.getMonthlyTrend().size());
        assertEquals(0, r2.getTopTags().size());
    }

    @Test
    void streamToListEmptySerializesAsBareArray_knownTrap() {
        // 已知坑（勿删，勿改断言）：stream().toList() 返回 JDK 不可变 ListN（final 类），
        // NON_FINAL typing 不为 final 类写类型 id → 空列表序列化为裸 []。
        // 缓存根值是 Object，读取时需 ["type", ...] 类型包裹 → SerializationException → 500。
        // 曾致 GET /api/albums 500（prod Redis 形态）：缓存方法返回值必须收进 ArrayList
        // （见 emptyListRoundTrips：ArrayList 序列化为 ["java.util.ArrayList",[]] 往返正常）。
        // 若未来升级序列化器修复此坑，删掉本测试并改回 toList() 即可。
        List<Category> empty = java.util.stream.Stream.<Category>of().toList();
        byte[] bytes = serializer.serialize(empty);
        String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("[]", json);
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
