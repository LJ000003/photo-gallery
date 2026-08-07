package com.hape.photogallery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 迁移在真实 MySQL 上的集成验证（H2 测试从不跑 Flyway，V1-V12 此前只在本地 MySQL / docker compose 跑过）。
 * <p>
 * 价值点：V9 的 ngram FULLTEXT 与 ddl-auto=validate（实体 ↔ 迁移 schema 一致性）只有真实 MySQL 能验——
 * 一个 migration 写错则 CI 全绿、生产启动即挂，本测试把这条防线搬进 CI。
 * <p>
 * 注意：dialect 必须显式覆盖——test resources 的 application.properties 声明了 H2Dialect
 * （同文件遮蔽 main 配置），@ServiceConnection 只覆盖 datasource url/账号/驱动，不覆盖 dialect。
 * 无 Docker 环境（@Testcontainers(disabledWithoutDocker)）自动跳过而非红。
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect"})
class FlywayMySqlMigrationTest {

    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0")).withDatabaseName("photodb");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allMigrations_shouldApplyOnRealMySql() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(12); // V1-V12
    }

    @Test
    void shareTokensTable_shouldExist() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"
                        + " AND table_name = 'share_tokens'", Integer.class);
        assertThat(count).isEqualTo(1); // V10迁移落地
    }

    @Test
    void v9FulltextIndex_shouldBeUsable() {
        // 空表也能执行：索引不存在时 MySQL 直接报错（Can't find FULLTEXT index）
        jdbc.queryForObject(
                "SELECT COUNT(*) FROM photos WHERE MATCH(name, description) AGAINST('测试' IN BOOLEAN MODE)",
                Long.class);
    }

    @Test
    void entitySchema_shouldMatchMigrations() {
        // ddl-auto=validate + @SpringBootTest 启动成功即证明实体与迁移 schema 一致
        assertThat(mysql.isRunning()).isTrue();
    }
}
