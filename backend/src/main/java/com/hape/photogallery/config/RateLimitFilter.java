package com.hape.photogallery.config;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_PER_SECOND = 10;

    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(1))
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getServletPath();
        String method = request.getMethod();
        // 认证端点全量限流：unlock 是 POST，challenge 是 GET（原实现漏掉 GET 导致 challenge 永不受限）
        boolean isAuthEndpoint = ("/api/v1/auth/unlock".equals(path) && "POST".equalsIgnoreCase(method))
                || ("/api/v1/auth/challenge".equals(path) && "GET".equalsIgnoreCase(method));

        if (!isAuthEndpoint) {
            chain.doFilter(request, response);
            return;
        }

        String ip = resolveIp(request);
        AtomicInteger count = counters.get(ip, k -> new AtomicInteger(0));

        if (count.incrementAndGet() > MAX_PER_SECOND) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String resolveIp(HttpServletRequest request) {
        // 优先 X-Real-IP（反向代理设置，客户端不可伪造），X-Forwarded-For 仅作 fallback
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
