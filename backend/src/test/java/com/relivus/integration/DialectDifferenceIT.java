package com.relivus.integration;

import com.relivus.dialect.PaginationSyntax;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 双数据库方言差异集成测试。
 *
 * <p>同一份查询在 MySQL 与 PostgreSQL 上验证：分页语法（LIMIT ?,? vs LIMIT ? OFFSET ?）、
 * 标识符引用、类型映射（布尔类型、分页结果一致）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DialectDifferenceIT extends AbstractDatabaseIT {

    @Test
    void paginationSyntaxDiffersByDialect() {
        assertThat(MYSQL_DIALECT.getPaginationSyntax()).isEqualTo(PaginationSyntax.MYSQL);
        assertThat(PG_DIALECT.getPaginationSyntax()).isEqualTo(PaginationSyntax.POSTGRESQL);

        String mysqlSql = MYSQL_DIALECT.getPaginationSyntax().apply("SELECT * FROM t", 20, 10);
        assertThat(mysqlSql).contains("LIMIT 20, 10");

        String pgSql = PG_DIALECT.getPaginationSyntax().apply("SELECT * FROM t", 20, 10);
        assertThat(pgSql).contains("LIMIT 10 OFFSET 20");
    }

    @Test
    void identifierQuotingDiffersByDialect() {
        assertThat(MYSQL_DIALECT.quoteIdentifier("user")).isEqualTo("`user`");
        assertThat(PG_DIALECT.quoteIdentifier("user")).isEqualTo("\"user\"");
    }

    @Test
    void booleanTypeMappingDiffersByDialect() {
        assertThat(MYSQL_DIALECT.getTypeMapping().booleanType()).isEqualTo("TINYINT(1)");
        assertThat(PG_DIALECT.getTypeMapping().booleanType()).isEqualTo("BOOLEAN");
    }

    @Test
    void paginationReturnsSameWindowOnBothDatabases() throws Exception {
        // MySQL
        execute(mysqlDataSource(),
                "DROP TABLE IF EXISTS page_test",
                "CREATE TABLE page_test (id INT PRIMARY KEY, v VARCHAR(16))");
        insertPageRows(new JdbcTemplate(mysqlDataSource()));
        Long mysqlCol = new JdbcTemplate(mysqlDataSource()).queryForObject(
                "SELECT COUNT(*) FROM ("
                        + MYSQL_DIALECT.getPaginationSyntax().apply("SELECT * FROM page_test", 2, 2)
                        + ") t", Long.class);
        assertThat(mysqlCol).isEqualTo(2L);

        // PostgreSQL
        execute(pgDataSource(),
                "DROP TABLE IF EXISTS page_test",
                "CREATE TABLE page_test (id INT PRIMARY KEY, v VARCHAR(16))");
        insertPageRows(new JdbcTemplate(pgDataSource()));
        Long pgCol = new JdbcTemplate(pgDataSource()).queryForObject(
                "SELECT COUNT(*) FROM ("
                        + PG_DIALECT.getPaginationSyntax().apply("SELECT * FROM page_test", 2, 2)
                        + ") t", Long.class);
        assertThat(pgCol).isEqualTo(2L);
    }

    private void insertPageRows(JdbcTemplate jdbc) {
        for (int i = 1; i <= 5; i++) {
            jdbc.update("INSERT INTO page_test(id, v) VALUES (?, ?)", i, "v" + i);
        }
    }
}