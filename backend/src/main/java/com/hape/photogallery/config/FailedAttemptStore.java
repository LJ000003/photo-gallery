package com.hape.photogallery.config;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Component
public class FailedAttemptStore {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BLOCK_MINUTES = 15;

    private final Cache<String, Integer> attempts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(BLOCK_MINUTES))
            .build();

    private final Cache<String, Boolean> blocked = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(BLOCK_MINUTES))
            .build();

    public boolean isBlocked(String ip) {
        return blocked.getIfPresent(ip) != null;
    }

    public void recordFailure(String ip) {
        // 使用原子 compute 防止并发 read-modify-write 丢失计数
        int count = attempts.asMap().compute(ip, (k, v) -> v == null ? 1 : v + 1);
        if (count >= MAX_ATTEMPTS) {
            blocked.put(ip, Boolean.TRUE);
        }
    }

    public void reset(String ip) {
        attempts.invalidate(ip);
        blocked.invalidate(ip);
    }

    public int remainingAttempts(String ip) {
        Integer count = attempts.getIfPresent(ip);
        if (count == null) return MAX_ATTEMPTS;
        return Math.max(0, MAX_ATTEMPTS - count);
    }
}
