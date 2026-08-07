package com.hape.photogallery.config;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.hape.photogallery.ApiResponse;
import com.hape.photogallery.entity.ShareToken;
import com.hape.photogallery.repository.ShareTokenRepository;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final MediaSignatureService mediaSignatureService;
    private final ObjectMapper objectMapper;
    private final ShareTokenRepository shareTokenRepository;

    public JwtAuthFilter(JwtService jwtService, MediaSignatureService mediaSignatureService,
                         ObjectMapper objectMapper, ShareTokenRepository shareTokenRepository) {
        this.jwtService = jwtService;
        this.mediaSignatureService = mediaSignatureService;
        this.objectMapper = objectMapper;
        this.shareTokenRepository = shareTokenRepository;
    }

    /** 错误响应统一走 ApiResponse JSON（此前 sendError 是纯 Servlet 错误体，与接口契约不一致） */
    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(status, message));
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

        // 图片端点优先校验短时签名（HMAC 时间桶）：签名只在管理员上下文签发的
        // 响应中出现（分享响应已剥离），绑定 photoId，无会话权限，URL 不泄漏 JWT
        // 注意：签名失效（时间桶滑出窗口 ~10 分钟）不得短路请求——浏览器历史/Service
        // Worker 缓存回源可能带旧签名，此时若请求头本有有效凭证应继续走 token 分支
        String uri = request.getRequestURI();
        boolean sigFailed = false;
        if (isImageFileRequest(uri)) {
            String sig = request.getParameter("sig");
            if (sig != null && !sig.isBlank()) {
                Long requestedPhotoId = extractPhotoIdFromImagePath(uri);
                long verifiedPhotoId = mediaSignatureService.verify(sig);
                if (requestedPhotoId != null && verifiedPhotoId == requestedPhotoId) {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    "media", null,
                                    List.of(new SimpleGrantedAuthority("ROLE_admin"))));
                    filterChain.doFilter(request, response);
                    return;
                }
                // 签名无效/过期：记录后继续尝试 token 凭证，全部失败才按无凭证处理
                sigFailed = true;
            }
        }

        // 仅图片文件端点允许 token 通过 query 参数传递（<img> 标签无法设置 HTTP header）
        // 后续会校验 viewer token 的 photoId 权限范围，风险可控
        if (token == null && isImageFileRequest(uri)) {
            token = request.getParameter("token");
        }

        if (token != null) {
            Claims claims = jwtService.verify(token);
            if (claims != null) {
                String role = claims.get("role", String.class);
                if ("admin".equals(role)) {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken("admin", null,
                                    List.of(new SimpleGrantedAuthority("ROLE_admin"))));
                } else if ("viewer".equals(role)) {
                    // legacy：旧 7 天 viewer JWT 分享链接过渡期兼容（新链接一律 DB token，
                    // 存量 JWT 最长 7 天自然失效且不可撤销——文档已注明）
                    @SuppressWarnings("unchecked")
                    List<?> photos = claims.get("photos", List.class);
                    List<Long> photoIds = null;
                    if (photos != null) {
                        photoIds = photos.stream().map(n -> ((Number) n).longValue()).toList();
                    }
                    String permission = claims.get("permission", String.class);
                    if (!applyViewerAuth(request, response, photoIds, permission)) {
                        return;
                    }
                }
            } else {
                // JWT 校验失败 → DB share token（新分享链接；撤销/过期即失效）。
                // 热路径代价：每张图一次 token 唯一索引查询——刻意不缓存以保持撤销即时生效。
                ShareToken st = shareTokenRepository.findByToken(token).orElse(null);
                if (st != null && st.getRevokedAt() == null && st.getExpiresAt() != null
                        && st.getExpiresAt().isAfter(LocalDateTime.now())) {
                    try {
                        List<Long> photoIds = objectMapper.readValue(st.getPhotoIds(),
                                objectMapper.getTypeFactory()
                                        .constructCollectionType(List.class, Long.class));
                        if (!applyViewerAuth(request, response, photoIds, st.getPermission())) {
                            return;
                        }
                    } catch (JsonProcessingException ex) {
                        // photo_ids 数据损坏：按未认证透传（Security 401 兜底）
                    }
                }
                // 无效/已撤销/已过期 token：不设认证，继续 chain（图片端点 403、
                // /api/v1/share/view 由 controller 以 404「分享链接无效或已过期」兜底）
            }
        }

        if (sigFailed && SecurityContextHolder.getContext().getAuthentication() == null) {
            // 签名失效且未建立任何认证：403（比 Security 兜底的 401 更精确说明原因）；
            // 上面 token 分支已设置认证（admin/viewer）时放行，不会走到这里
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "图片签名无效或已过期");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * viewer 鉴权（legacy JWT 与 DB share token 共用）：设 sharePhotoIds/sharePermission
     * attribute + ROLE_viewer；图片请求校验 photoId 白名单与 download 权限。
     * 返回 false 表示已写错误响应，调用方应终止链。
     */
    private boolean applyViewerAuth(HttpServletRequest request, HttpServletResponse response,
                                    List<Long> photoIds, String permission) throws IOException {
        if (photoIds != null) {
            request.setAttribute("sharePhotoIds", photoIds);
        }
        request.setAttribute("sharePermission", permission);

        // 校验 viewer 访问图片文件端点时，photo ID 必须在 sharePhotoIds 范围内
        Long requestedPhotoId = extractPhotoIdFromImagePath(request.getRequestURI());
        if (requestedPhotoId != null) {
            if (photoIds == null || !photoIds.contains(requestedPhotoId)) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "无权限访问该照片");
                return false;
            }
            // 强制执行分享权限：view 仅可查看缩略图/WebP，禁止下载原图；
            // permission 缺失时按最保守处理（同样拒绝下载）
            if (isFileRequest(request.getRequestURI()) && !"download".equals(permission)) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "该分享链接仅可查看，不可下载");
                return false;
            }
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("viewer", null,
                        List.of(new SimpleGrantedAuthority("ROLE_viewer"))));
        return true;
    }

    private static final Pattern PHOTO_IMAGE_PATH = Pattern.compile(
            "/api/v1/photos/(\\d+)/(?:thumbnail|webp|file)");

    private boolean isImageFileRequest(String uri) {
        return PHOTO_IMAGE_PATH.matcher(uri).find();
    }

    /** 是否为原图下载端点（仅 /file，thumbnail/webp 允许 view 权限查看） */
    private boolean isFileRequest(String uri) {
        return uri.endsWith("/file");
    }

    private Long extractPhotoIdFromImagePath(String uri) {
        Matcher m = PHOTO_IMAGE_PATH.matcher(uri);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                // 超长数字（> Long.MAX_VALUE）→ 视为无有效 photoId（403/401 而非 500）
                return null;
            }
        }
        return null;
    }
}
