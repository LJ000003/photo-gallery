package com.hape.photogallery.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Duration;
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

        // PageImpl → 序列化为 JSON 对象而非数组，并提供反序列化构造函数
        mapper.addMixIn(PageImpl.class, PageImplMixin.class);
        mapper.addMixIn(PageRequest.class, PageRequestMixin.class);
        mapper.addMixIn(Sort.class, SortMixin.class);

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

    /** PageRequest 反序列化 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    abstract static class PageRequestMixin {
        @JsonCreator
        public PageRequestMixin(
                @JsonProperty("page") int page,
                @JsonProperty("size") int size,
                @JsonProperty("sort") Sort sort) {}
    }

    /** Sort 反序列化 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    abstract static class SortMixin {
        @JsonCreator
        public SortMixin(@JsonProperty("orders") List<Sort.Order> orders) {}
    }
}
