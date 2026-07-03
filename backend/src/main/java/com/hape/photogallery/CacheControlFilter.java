package com.hape.photogallery;

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
                response.setHeader("Cache-Control", "max-age=2592000");
            } else if (!response.containsHeader("Cache-Control")) {
                response.setHeader("Cache-Control", "max-age=5, must-revalidate, private");
            }
        } else {
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        }

        filterChain.doFilter(request, response);
    }
}
