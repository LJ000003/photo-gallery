package com.hape.photogallery.config;

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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
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
        ObjectMapper mapper = new ObjectMapper();

        // Java 8 时间类型支持
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // PageImpl / PageRequest → 序列化为 JSON 对象并提供反序列化构造函数
        mapper.addMixIn(PageImpl.class, PageImplMixin.class);
        mapper.addMixIn(PageRequest.class, PageRequestMixin.class);

        // Sort 使用自定义反序列化器（Sort 的构造方法是 private 的，mixin @JsonCreator 无法正确工作）
        SimpleModule sortModule = new SimpleModule();
        sortModule.addDeserializer(Sort.class, new SortDeserializer());
        mapper.registerModule(sortModule);

        // 启用 default typing，写入 @class 元数据以支持多态反序列化
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30))
                .serializeValuesWith(SerializationPair.fromSerializer(serializer))
                .prefixCacheNameWith("pg:")
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
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
            if (ordersNode != null && ordersNode.isArray() && !ordersNode.isEmpty()) {
                List<Sort.Order> orders = new ArrayList<>();
                for (JsonNode orderNode : ordersNode) {
                    orders.add(p.getCodec().treeToValue(orderNode, Sort.Order.class));
                }
                return Sort.by(orders);
            }
            return Sort.unsorted();
        }
    }
}
