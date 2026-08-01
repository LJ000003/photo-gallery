package com.hape.photogallery.config;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 优先从 Authorization header 取 token
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }

        // 仅图片文件端点允许 token 通过 query 参数传递（<img> 标签无法设置 HTTP header）
        // 后续会校验 viewer token 的 photoId 权限范围，风险可控
        if (token == null && isImageFileRequest(request.getRequestURI())) {
            token = request.getParameter("token");
        }

        if (token != null) {
            Claims claims = jwtService.verify(token);
            if (claims != null) {
                String role = claims.get("role", String.class);
                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role));

                if ("viewer".equals(role)) {
                    @SuppressWarnings("unchecked")
                    List<?> photos = claims.get("photos", List.class);
                    List<Long> photoIds = null;
                    if (photos != null) {
                        photoIds = photos.stream().map(n -> ((Number) n).longValue()).toList();
                        request.setAttribute("sharePhotoIds", photoIds);
                    }
                    request.setAttribute("sharePermission", claims.get("permission", String.class));

                    // 校验 viewer 访问图片文件端点时，photo ID 必须在 sharePhotoIds 范围内
                    Long requestedPhotoId = extractPhotoIdFromImagePath(request.getRequestURI());
                    if (requestedPhotoId != null) {
                        if (photoIds == null || !photoIds.contains(requestedPhotoId)) {
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, "无权限访问该照片");
                            return;
                        }
                    }
                }

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(role, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    private static final Pattern PHOTO_IMAGE_PATH = Pattern.compile(
            "/api/v1/photos/(\\d+)/(?:thumbnail|webp|file)");

    private boolean isImageFileRequest(String uri) {
        return PHOTO_IMAGE_PATH.matcher(uri).find();
    }

    private Long extractPhotoIdFromImagePath(String uri) {
        Matcher m = PHOTO_IMAGE_PATH.matcher(uri);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        return null;
    }
}
