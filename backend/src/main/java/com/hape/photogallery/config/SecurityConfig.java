package com.hape.photogallery.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.hape.photogallery.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RateLimitFilter rateLimitFilter;
    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(RateLimitFilter rateLimitFilter, JwtAuthFilter jwtAuthFilter,
                          ObjectMapper objectMapper) {
        this.rateLimitFilter = rateLimitFilter;
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    /** 认证失败响应统一 ApiResponse JSON（默认 entry point 返回空体 403） */
    private void writeAuthError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(status, message));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsSource()))
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true).maxAgeInSeconds(31536000))
                .contentTypeOptions(cto -> {})  // X-Content-Type-Options: nosniff
                .frameOptions(frame -> frame.deny())  // X-Frame-Options: DENY
                // CSP：style-src 需 'unsafe-inline'（antd cssinjs 注入 <style> + 内联 style 属性）；
                // img-src 允许高德瓦片（Leaflet 底图 webst0-4/webrd0-4.is.autonavi.com）；
                // static/index.html 无内联 script，script-src 'self' 即可。
                // 注意 dev swagger-ui 的 CSS 引用 fonts.gstatic.com，font-src 未放行 → 仅字体降级（prod 已禁用 springdoc）
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; "
                        + "img-src 'self' data: blob: https://*.is.autonavi.com; "
                        + "style-src 'self' 'unsafe-inline'; "
                        + "script-src 'self'; "
                        + "connect-src 'self'; "
                        + "font-src 'self' data:; "
                        + "worker-src 'self' blob:; "
                        + "object-src 'none'; "
                        + "base-uri 'self'; "
                        + "form-action 'self'; "
                        + "frame-ancestors 'none'")
                )
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 启用 Basic Auth——仅 /actuator/prometheus（hasRole MONITOR）实际受保护，
            // 其余端点仍走 JWT 过滤器链；未带 Basic 凭据的 API 请求不受影响。
            // 注意必须在本处先于 exceptionHandling 配置——Spring Security 6 中 httpBasic()
            // 会覆盖 authenticationEntryPoint，若在其后调用会破坏下方自定义 JSON 401 响应
            .httpBasic(h -> {})
            // 未认证 401 / 权限不足 403 统一 ApiResponse JSON（此前默认空体；前端对 401/403 同样登出处理，无影响）
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) ->
                        writeAuthError(res, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已过期"))
                .accessDeniedHandler((req, res, ex) ->
                        writeAuthError(res, HttpServletResponse.SC_FORBIDDEN, "无权限执行该操作"))
            )
            .addFilterBefore(new TraceIdFilter(), SecurityContextHolderFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, JwtAuthFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/v1/auth/unlock")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/auth/challenge")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/share/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/share/view"))
                    .hasAnyAuthority("ROLE_admin", "ROLE_viewer")
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/photos/*/thumbnail"))
                    .hasAnyAuthority("ROLE_admin", "ROLE_viewer")
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/photos/*/webp"))
                    .hasAnyAuthority("ROLE_admin", "ROLE_viewer")
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/photos/*/file"))
                    .hasAnyAuthority("ROLE_admin", "ROLE_viewer")
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/**"))
                    .hasAuthority("ROLE_admin")
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/actuator/health")).permitAll()
                // prometheus 指标公网裸奔 → Basic Auth（MONITOR 角色，凭据来自 env，
                // 未配置时 Boot 默认用户非 MONITOR → 403，端点仍不可访问）
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/actuator/prometheus"))
                    .hasRole("MONITOR")
                .requestMatchers(AntPathRequestMatcher.antMatcher("/swagger-ui/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/v3/api-docs/**")).permitAll()
                .requestMatchers(
                    AntPathRequestMatcher.antMatcher("/"),
                    AntPathRequestMatcher.antMatcher("/index.html"),
                    AntPathRequestMatcher.antMatcher("/assets/**"),
                    AntPathRequestMatcher.antMatcher("/manifest.webmanifest"),
                    AntPathRequestMatcher.antMatcher("/pwa-icon.svg"),
                    AntPathRequestMatcher.antMatcher("/favicon.ico"),
                    AntPathRequestMatcher.antMatcher("/*.js"),
                    AntPathRequestMatcher.antMatcher("/*.css"),
                    AntPathRequestMatcher.antMatcher("/*.svg"),
                    AntPathRequestMatcher.antMatcher("/*.webmanifest")
                ).permitAll()
                .anyRequest().hasAuthority("ROLE_admin")
            )
            .formLogin(fl -> fl.disable());

        return http.build();
    }

    /**
     * 监控抓取凭据（Grafana/Prometheus basic_auth 对应）。
     * 条件加载：dev/test 未配置 monitoring.* 时该 Bean 不存在，
     * Boot 默认随机密码用户无 MONITOR 角色 → 端点恒 403，不泄漏指标。
     * {noop} 明码：内网 localhost 抓取凭据，可接受；如需更强可换 {bcrypt} 预哈希。
     */
    @Bean
    @ConditionalOnProperty(name = {"monitoring.username", "monitoring.password"})
    UserDetailsService monitoringUserDetailsService(
            @Value("${monitoring.username}") String username,
            @Value("${monitoring.password}") String password) {
        return new InMemoryUserDetailsManager(
                User.withUsername(username).password("{noop}" + password).roles("MONITOR").build());
    }

    @Value("${cors.allowed-origins:http://localhost:*,https://hape233.online}")
    private String corsOrigins;

    private CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(corsOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id", "Accept"));
        config.setExposedHeaders(List.of("X-Trace-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/share/**", config);
        return source;
    }
}
