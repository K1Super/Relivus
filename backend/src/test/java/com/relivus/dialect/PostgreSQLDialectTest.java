package com.relivus.dialect;

import com.relivus.schema.model.ColumnMetadata;
import org.junit.jupiter.api.Test;

import java.sql.DatabaseMetaData;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PostgreSQL 方言测试：标识符引用、SQL 构建、supports。
 */
class PostgreSQLDialectTest {

    private final PostgreSQLDialect dialect = new PostgreSQLDialect();

    @Test
    void quotesIdentifiersWithDoubleQuotes() {
        assertThat(dialect.quoteIdentifier("users")).isEqualTo("\"users\"");
        assertThat(dialect.quoteIdentifier("a\"b")).isEqualTo("\"a\"\"b\"");
    }

    @Test
    void buildsJdbcUrl() {
        assertThat(dialect.buildJdbcUrl("localhost", 5432, "relivus"))
                .isEqualTo("jdbc:postgresql://localhost:5432/relivus");
    }

    @Test
    void supportsMatchesProductName() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        assertThat(dialect.supports(metaData)).isTrue();
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");
        assertThat(dialect.supports(metaData)).isFalse();
    }

    @Test
    void buildsSqlWithQuotedIdentifiers() {
        List<ColumnMetadata> columns = List.of(
                new ColumnMetadata("id", "BIGINT", false, null, true, List.of()),
                new ColumnMetadata("age", "INT", true, null, false, List.of()));

        assertThat(dialect.buildInsertSql("users", columns))
                .isEqualTo("INSERT INTO users (\"id\", \"age\") VALUES (?, ?)");

        assertThat(dialect.buildSelectSql("users", List.of("age"), "age > 18"))
                .isEqualTo("SELECT \"age\" FROM users WHERE age > 18");

        assertThat(dialect.buildUpdateSql("users", List.of("age"), "\"id\" = ?"))
                .isEqualTo("UPDATE users SET \"age\"=? WHERE \"id\" = ?");
    }

    @Test
    void providesTypeMappingAndPagination() {
        assertThat(dialect.getPaginationSyntax()).isEqualTo(PaginationSyntax.POSTGRESQL);
        assertThat(dialect.getTypeMapping().booleanType()).isEqualTo("BOOLEAN");
        assertThat(dialect.getTypeMapping().binaryType()).isEqualTo("BYTEA");
    }
}