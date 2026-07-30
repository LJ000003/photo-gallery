package com.hape.photogallery.config;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Component
public class NonceStore {

    private final Cache<String, Boolean> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    public String generate() {
        String nonce = UUID.randomUUID().toString();
        cache.put(nonce, Boolean.TRUE);
        return nonce;
    }

    /** 一次性消费：存在则删除并返回 true，不存在返回 false */
    public boolean consume(String nonce) {
        if (cache.getIfPresent(nonce) != null) {
            cache.invalidate(nonce);
            return true;
        }
        return false;
    }
}
