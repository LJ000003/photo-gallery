package com.hape.photogallery.service;

import javax.sql.DataSource;
import java.sql.Connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

/**
 * MySQL 风格 FULLTEXT（MATCH...AGAINST）支持探测（从 PhotoService 拆出）：
 * 仅 MySQL/MariaDB 支持，H2 等不支持（FULLTEXT 查询在 H2 上直接语法错误 → 500）。
 * 惰性探测一次后缓存；探测失败或未注入 DataSource（单测 mock 场景）按「支持」处理，
 * 保证 MySQL 生产语义不变。
 */
@Component
public class FullTextProbe {

    private static final Logger log = LoggerFactory.getLogger(FullTextProbe.class);

    private final DataSource dataSource;

    /** 探测结果缓存（null = 未探测；惰性探测避免启动期连接失败被缓存成 true） */
    private Boolean supported;

    public FullTextProbe(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isSupported() {
        if (supported == null) {
            supported = probe();
        }
        return supported;
    }

    private boolean probe() {
        if (dataSource == null) return true;
        Connection conn = null;
        try {
            conn = DataSourceUtils.getConnection(dataSource);
            String product = conn.getMetaData().getDatabaseProductName();
            return product != null && (product.contains("MySQL") || product.contains("MariaDB"));
        } catch (Exception e) {
            log.warn("数据库类型探测失败，搜索按 FULLTEXT 处理: {}", e.getMessage());
            return true;
        } finally {
            if (conn != null) DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }
}
