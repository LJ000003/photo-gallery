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
        int count = attempts.get(ip, k -> 0) + 1;
        attempts.put(ip, count);
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
