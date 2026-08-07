package com.hape.photogallery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 分享凭证：替代 7 天 viewer JWT——高熵随机 token 落库，
 * 支持撤销（revokedAt）与过期校验；photoIds 存规范化 JSON 数组字符串。
 */
@Entity
@Table(name = "share_tokens")
public class ShareToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String photoIds;

    @Column(nullable = false, length = 16)
    private String permission;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 未撤销且未过期 */
    public boolean isActive() {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPhotoIds() { return photoIds; }
    public void setPhotoIds(String photoIds) { this.photoIds = photoIds; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
