package com.relivus.dialect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页语法差异测试（DOC-01 差异表）。
 */
class PaginationSyntaxTest {

    @Test
    void mysqlUsesLimitOffsetCommaSize() {
        assertThat(PaginationSyntax.MYSQL.apply("SELECT * FROM t", 10, 5))
                .isEqualTo("SELECT * FROM t LIMIT 10, 5");
    }

    @Test
    void postgresqlUsesLimitSizeOffset() {
        assertThat(PaginationSyntax.POSTGRESQL.apply("SELECT * FROM t", 10, 5))
                .isEqualTo("SELECT * FROM t LIMIT 5 OFFSET 10");
    }
}