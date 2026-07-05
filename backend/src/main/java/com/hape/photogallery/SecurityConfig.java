package com.hape.photogallery;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.Filter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RateLimitFilter rateLimitFilter;
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(RateLimitFilter rateLimitFilter, JwtAuthFilter jwtAuthFilter) {
        this.rateLimitFilter = rateLimitFilter;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, JwtAuthFilter.class)
            .authorizeHttpRequests(auth -> auth
                // 认证端点公开
                .requestMatchers(HttpMethod.POST, "/api/auth/unlock").permitAll()
                // 分享落地面公开（转发到 SPA）
                .requestMatchers(HttpMethod.GET, "/share/**").permitAll()
                // 读操作需 viewer 以上角色（管理员 + 分享链接用户）
                .requestMatchers(HttpMethod.GET, "/api/**").hasAnyAuthority("ROLE_admin", "ROLE_viewer")
                // Actuator 健康检查公开
                .requestMatchers("/actuator/health").permitAll()
                // Actuator 健康检查公开
                .requestMatchers("/actuator/health").permitAll()
                // Swagger / SpringDoc 公开
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // 静态资源公开
                .requestMatchers("/", "/index.html", "/assets/**", "/*.js", "/*.css", "/*.ico").permitAll()
                // 写操作需 admin
                .anyRequest().hasAuthority("ROLE_admin")
            )
            .formLogin(fl -> fl.disable())
            .httpBasic(hb -> hb.disable());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/share/**", config);
        return source;
    }
}
