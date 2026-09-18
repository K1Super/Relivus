package com.relivus.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 迁移集成测试（DOC-08 / DOC-05 统一任务表）。
 *
 * <p>双数据库空库执行迁移脚本（{@code db/migration/{mysql,postgresql}}），
 * 验证 df_connection / df_task / df_task_log / df_mask_mapping / df_audit_log 全部就位。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlywayMigrationIT extends AbstractDatabaseIT {

    @Test
    void migratesMysqlEmptyDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(mysqlDataSource())
                .locations("classpath:db/migration/mysql")
                .cleanDisabled(true)
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        assertMetaTablesPresent(new JdbcTemplate(mysqlDataSource()), MYSQL_DIALECT.name());
    }

    @Test
    void migratesPostgresEmptyDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(pgDataSource())
                .locations("classpath:db/migration/postgresql")
                .cleanDisabled(true)
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        assertMetaTablesPresent(new JdbcTemplate(pgDataSource()), PG_DIALECT.name());
    }

    private void assertMetaTablesPresent(JdbcTemplate jdbc, String dialect) {
        String sql = dialect.equals("mysql")
                ? "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()"
                : "SELECT tablename FROM pg_tables WHERE schemaname = 'public'";
        List<String> tables = jdbc.queryForList(sql, String.class);
        assertThat(tables)
                .anyMatch(t -> t.equalsIgnoreCase("df_connection"))
                .anyMatch(t -> t.equalsIgnoreCase("df_task"))
                .anyMatch(t -> t.equalsIgnoreCase("df_task_log"))
                .anyMatch(t -> t.equalsIgnoreCase("df_mask_mapping"))
                .anyMatch(t -> t.equalsIgnoreCase("df_audit_log"));
    }
}