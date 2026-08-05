-- 分享 token 数据库化（P0-#6/#7）：替代 7 天 viewer JWT，支持撤销/过期校验
-- photo_ids 存 JSON 数组字符串（"[1,2,3]"），由应用层 ObjectMapper 规范化（排序去重）后读写
-- token 列 COLLATE utf8mb4_bin：大小写敏感（MySQL 默认 collation 下 UNIQUE 大小写不敏感）
CREATE TABLE share_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    photo_ids TEXT NOT NULL,
    permission VARCHAR(16) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_share_tokens_token UNIQUE (token),
    KEY idx_share_tokens_expires (expires_at)
);
