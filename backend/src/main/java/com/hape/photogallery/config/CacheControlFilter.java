package com.hape.photogallery.config;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CacheControlFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod())) {
            if (path.matches(".*/photos/\\d+/file$")) {
                // 30 天无 private 的共享缓存会在 transform 后最长 30 天显示旧图；
                // 签名时间桶窗口 2×300s=600s，取半窗口 300s 留余量（private 禁止共享缓存介入）
                response.setHeader("Cache-Control", "private, max-age=300");
            } else if (!response.containsHeader("Cache-Control")) {
                response.setHeader("Cache-Control", "max-age=0, must-revalidate, private");
            }
        } else {
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        }

        filterChain.doFilter(request, response);
    }
}
