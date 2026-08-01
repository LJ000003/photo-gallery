package com.hape.photogallery.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30))
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(redisObjectMapper())))
                .prefixCacheNameWith("pg:")
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }

    /** 构建 Redis 缓存专用的 ObjectMapper，供测试复用 */
    static ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Java 8 时间类型支持
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 部分缓存 DTO 只有 getter（PhotoResponse / TimelineItem / MapItem 等），
        // 放开字段可见性后才能反序列化（否则报 no Creators / 无法赋值）
        mapper.setVisibility(VisibilityChecker.Std.defaultInstance()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY));

        // PageImpl / PageRequest → 序列化为 JSON 对象并提供反序列化构造函数
        mapper.addMixIn(PageImpl.class, PageImplMixin.class);
        mapper.addMixIn(PageRequest.class, PageRequestMixin.class);

        // Sort 使用自定义反序列化器（Sort 的构造方法是 private 的，mixin @JsonCreator 无法正确工作）
        SimpleModule sortModule = new SimpleModule();
        sortModule.addDeserializer(Sort.class, new SortDeserializer());
        mapper.registerModule(sortModule);

        // 启用 default typing，使用白名单限制可反序列化的类型（防止 RCE）。
        // 缓存值的根类型是 Object，default typing 会把具体类型写入 @class，
        // 白名单必须按「具体类型」放行，而不是按基类型：
        //  - java.util.*              集合实现（ArrayList / Collections$UnmodifiableRandomAccessList / HashSet 等）
        //  - org.springframework.data.*   分页相关（PageImpl / PageRequest / Sort$Order 等）
        //  - com.hape.photogallery.*     缓存的实体与 DTO（Category / PhotoResponse / TimelineItem / MapItem 等）
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("java.util.")
                .allowIfSubType("org.springframework.data.")
                .allowIfSubType("com.hape.photogallery.")
                .build();
        mapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        return mapper;
    }

    // ── Jackson Mixins ──────────────────────────────────────────

    /** PageImpl → shape=OBJECT 防止被序列化为 JSON 数组；@JsonCreator 支持反序列化 */
    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    abstract static class PageImplMixin {
        @JsonCreator
        public PageImplMixin(
                @JsonProperty("content") List<?> content,
                @JsonProperty("pageable") Pageable pageable,
                @JsonProperty("totalElements") long total) {}
    }

    /** PageRequest 反序列化 — Spring Data 序列化用 pageNumber/pageSize */
    @JsonIgnoreProperties(ignoreUnknown = true)
    abstract static class PageRequestMixin {
        @JsonCreator
        public PageRequestMixin(
                @JsonProperty("pageNumber") int page,
                @JsonProperty("pageSize") int size,
                @JsonProperty("sort") Sort sort) {}
    }

    /**
     * Sort 自定义反序列化器。
     * Sort 的构造方法是 private 的，无法通过 mixin @JsonCreator 正确实例化，
     * 必须使用公有的 Sort.by() 工厂方法。
     */
    static class SortDeserializer extends StdDeserializer<Sort> {
        SortDeserializer() { super(Sort.class); }

        @Override
        public Sort deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            JsonNode ordersNode = node.get("orders");
            if (ordersNode != null && ordersNode.isArray()) {
                // default typing 会把 List 序列化成 ["java.util.ArrayList", [...]]，先解包
                if (ordersNode.size() == 2 && ordersNode.get(0).isTextual() && ordersNode.get(1).isArray()) {
                    ordersNode = ordersNode.get(1);
                }
                if (!ordersNode.isEmpty()) {
                    List<Sort.Order> orders = new ArrayList<>();
                    for (JsonNode orderNode : ordersNode) {
                        // Sort.Order 没有 @JsonCreator，直接按字段构造
                        String property = orderNode.path("property").asText();
                        Sort.Direction direction = Sort.Direction
                                .fromString(orderNode.path("direction").asText("ASC"));
                        boolean ignoreCase = orderNode.path("ignoreCase").asBoolean(false);
                        Sort.NullHandling nullHandling = Sort.NullHandling
                                .valueOf(orderNode.path("nullHandling").asText("NATIVE"));
                        orders.add(new Sort.Order(direction, property, ignoreCase, nullHandling));
                    }
                    return Sort.by(orders);
                }
            }
            return Sort.unsorted();
        }
    }
}
