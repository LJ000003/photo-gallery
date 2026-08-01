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

    /** 一次性消费：原子地检查并删除，防止 TOCTOU 竞态导致 nonce 重放 */
    public boolean consume(String nonce) {
        return cache.asMap().remove(nonce) != null;
    }
}
