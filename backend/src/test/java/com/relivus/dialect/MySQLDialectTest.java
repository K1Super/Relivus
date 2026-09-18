package com.relivus.dialect;

import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.ForeignKeyMetadata;
import com.relivus.schema.model.TableMetadata;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MySQL 方言测试：标识符引用、SQL 构建、ENUM 解析、supports。
 */
class MySQLDialectTest {

    private final MySQLDialect dialect = new MySQLDialect();

    @Test
    void quotesIdentifiersWithBackticks() {
        assertThat(dialect.quoteIdentifier("users")).isEqualTo("`users`");
        assertThat(dialect.quoteIdentifier("a`b")).isEqualTo("`a``b`");
    }

    @Test
    void buildsJdbcUrl() {
        assertThat(dialect.buildJdbcUrl("localhost", 3306, "relivus"))
                .isEqualTo("jdbc:mysql://localhost:3306/relivus?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    }

    @Test
    void supportsMatchesProductName() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");
        assertThat(dialect.supports(metaData)).isTrue();
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        assertThat(dialect.supports(metaData)).isFalse();
    }

    @Test
    void supportsPropagatesSqlException() {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        try {
            when(metaData.getDatabaseProductName()).thenThrow(new SQLException("boom"));
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
        assertThatThrownBy(() -> dialect.supports(metaData)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildsInsertSelectUpdateSql() {
        List<ColumnMetadata> columns = List.of(
                new ColumnMetadata("id", "INT", false, null, true, List.of()),
                new ColumnMetadata("name", "VARCHAR", true, null, false, List.of()));

        assertThat(dialect.buildInsertSql("users", columns))
                .isEqualTo("INSERT INTO users (`id`, `name`) VALUES (?, ?)");
        assertThatThrownBy(() -> dialect.buildInsertSql("users", List.of()))
                .isInstanceOf(com.relivus.common.exception.RelivusException.class);

        assertThat(dialect.buildSelectSql("users", List.of("id", "name"), "id > 0"))
                .isEqualTo("SELECT `id`, `name` FROM users WHERE id > 0");
        assertThat(dialect.buildSelectSql("users", null, null)).isEqualTo("SELECT * FROM users");

        assertThat(dialect.buildUpdateSql("users", List.of("name"), "id = ?"))
                .isEqualTo("UPDATE users SET `name`=? WHERE id = ?");
        assertThatThrownBy(() -> dialect.buildUpdateSql("users", List.of(), null))
                .isInstanceOf(com.relivus.common.exception.RelivusException.class);
    }

    @Test
    void parsesEnumValues() {
        assertThat(MySQLDialect.parseEnumValues("'A','B'")).containsExactly("A", "B");
        assertThat(MySQLDialect.parseEnumValues("'it''s'")).containsExactly("it's");
        assertThat(MySQLDialect.parseEnumValues("")).isEmpty();
    }

    @Test
    void providesTypeMappingAndPagination() {
        assertThat(dialect.getPaginationSyntax()).isEqualTo(PaginationSyntax.MYSQL);
        assertThat(dialect.getTypeMapping().booleanType()).isEqualTo("TINYINT(1)");
    }

    @Test
    void introspectSchemaFailsFast_whenConnectionBroken() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenThrow(new SQLException("down"));
        assertThatThrownBy(() -> dialect.introspectSchema(connection))
                .isInstanceOf(com.relivus.common.exception.RelivusException.class)
                .hasFieldOrPropertyWithValue("code", com.relivus.common.exception.ErrorCode.SCHEMA_INTROSPECTION_FAILED.getCode());
    }

    @Test
    void filtersSystemTables() {
        assertThat(dialect.isSystemTable("mysql_user", null)).isTrue();
        assertThat(dialect.isSystemTable("sys_tmp", null)).isTrue();
        assertThat(dialect.isSystemTable("performance_schema_x", null)).isTrue();
        assertThat(dialect.isSystemTable("information_schema_x", null)).isTrue();
        assertThat(dialect.isSystemTable("ndbinfo_x", null)).isTrue();
        assertThat(dialect.isSystemTable("innodb_buf", null)).isTrue();
        assertThat(dialect.isSystemTable("users", null)).isFalse();
    }

    @Test
    void resolvesCatalogAndNullSchema() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getCatalog()).thenReturn("mydb");
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        assertThat(dialect.catalog(connection, meta)).isEqualTo("mydb");
        assertThat(dialect.schema(connection, meta)).isNull();
    }
}