package com.relivus.generator;

import com.relivus.dialect.MySQLDialect;
import com.relivus.schema.model.ColumnMetadata;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 批量插入器测试（DOC-08 补充：行覆盖）。
 *
 * <p>H2 真实 SQL：批量插入生效、自增主键逐批回填。
 */
class BatchInserterTest {

    private static final MySQLDialect DIALECT = new MySQLDialect();
    private static final List<ColumnMetadata> COLS = List.of(
            new ColumnMetadata("name", "VARCHAR", false, null, false, List.of()),
            new ColumnMetadata("age", "INT", true, null, false, List.of()));

    private JdbcDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:bi;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(dataSource);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS people");
            s.execute("CREATE TABLE people (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64) NOT NULL, age INT)");
        }
    }

    @Test
    void insertBulkInsertsAllRows() {
        BatchInserter inserter = new BatchInserter(jdbc, DIALECT);
        List<Object[]> rows = List.of(
                new Object[]{"alice", 30},
                new Object[]{"bob", 25});
        inserter.insertBulk("people", COLS, rows, 2);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM people", Long.class);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void insertReturningIdsBackfillsAutoIncrementInOrder() {
        BatchInserter inserter = new BatchInserter(jdbc, DIALECT);
        List<Object[]> rows = List.of(
                new Object[]{"alice", 30},
                new Object[]{"bob", 25});
        List<Long> ids = inserter.insertReturningIds("people", COLS, rows, 1);
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isNotNull();
        assertThat(ids.get(1)).isEqualTo(ids.get(0) + 1);
    }

    @Test
    void insertAcrossMultipleBatchesReturnsAllIds() {
        BatchInserter inserter = new BatchInserter(jdbc, DIALECT);
        List<Object[]> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            rows.add(new Object[]{"p" + i, i});
        }
        List<Long> ids = inserter.insertReturningIds("people", COLS, rows, 3);
        assertThat(ids).hasSize(7);
        assertThat(ids.stream().noneMatch(java.util.Objects::isNull)).isTrue();
    }
}