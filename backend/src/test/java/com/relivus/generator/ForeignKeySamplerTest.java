package com.relivus.generator;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dialect.MySQLDialect;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 外键采样器测试（DOC-08 补充：行覆盖）。
 *
 * <p>H2 真实 SQL 驱动：统一/倾斜采样、空父表拒绝、分页取数。
 */
class ForeignKeySamplerTest {

    private static final MySQLDialect DIALECT = new MySQLDialect();

    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:fks;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS parent_t");
            s.execute("CREATE TABLE parent_t (id BIGINT PRIMARY KEY, name VARCHAR(50))");
        }
    }

    @Test
    void sampleUniformReturnsExistingKey() {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.batchUpdate("INSERT INTO parent_t(id, name) VALUES (?, ?)",
                java.util.List.of(new Object[]{1L, "a"}, new Object[]{2L, "b"}, new Object[]{3L, "c"}));
        ForeignKeySampler sampler = new ForeignKeySampler(dataSource, DIALECT);
        for (int i = 0; i < 20; i++) {
            Object key = sampler.sample("parent_t", "id", i, SamplingStrategy.UNIFORM);
            assertThat(key).isInstanceOf(Number.class);
            long value = ((Number) key).longValue();
            assertThat(value).isBetween(1L, 3L);
        }
    }

    @Test
    void sampleZipfReturnsExistingKey() {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.batchUpdate("INSERT INTO parent_t(id, name) VALUES (?, ?)",
                java.util.List.of(new Object[]{1L, "a"}, new Object[]{2L, "b"}, new Object[]{3L, "c"}));
        ForeignKeySampler sampler = new ForeignKeySampler(dataSource, DIALECT);
        for (int i = 0; i < 20; i++) {
            Object key = sampler.sample("parent_t", "id", i, SamplingStrategy.ZIPF);
            assertThat(((Number) key).longValue()).isBetween(1L, 3L);
        }
    }

    @Test
    void sampleSingleRowTableReturnsThatKey() {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO parent_t(id, name) VALUES (42, 'only')");
        ForeignKeySampler sampler = new ForeignKeySampler(dataSource, DIALECT);
        Object key = sampler.sample("parent_t", "id", 100, SamplingStrategy.UNIFORM);
        assertThat(((Number) key).longValue()).isEqualTo(42L);
    }

    @Test
    void emptyParentRejectedAsConnectionFailed() {
        ForeignKeySampler sampler = new ForeignKeySampler(dataSource, DIALECT);
        assertThatThrownBy(() -> sampler.sample("parent_t", "id", 0, SamplingStrategy.UNIFORM))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CONNECTION_FAILED.getCode());
    }
}