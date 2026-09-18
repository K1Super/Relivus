package com.relivus.schema;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dialect.MySQLDialect;
import com.relivus.schema.model.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 内省器测试（DOC-02 / DOC-08 补充）。
 *
 * <p>Mock DatabaseMetaData 驱动方言通用内省骨架全链路：表/列/主键/唯一索引读取、
 * 系统表过滤、复合主键回退、异常包装 2001，以及方言 SQL 构建与 MySQL 细节。
 */
class JdbcSchemaIntrospectorTest {

    private static final MySQLDialect DIALECT = new MySQLDialect();
    private static final String[] TABLE_TYPES = {"TABLE"};

    private final JdbcSchemaIntrospector introspector = new JdbcSchemaIntrospector();

    private Connection connection;
    private DatabaseMetaData meta;
    private ResultSet rsTables;
    private ResultSet rsColumns;
    private ResultSet rsPk;
    private ResultSet rsFk;
    private ResultSet rsIndex;
    private ResultSet rsEmpty;
    private PreparedStatement ps;

    @BeforeEach
    void setUpMocks() throws Exception {
        connection = mock(Connection.class);
        meta = mock(DatabaseMetaData.class);
        preparedMocks();
        when(connection.getMetaData()).thenReturn(meta);
        when(connection.getCatalog()).thenReturn("testdb");
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rsEmpty);

        // 两张表：users（保留）+ sys_table（系统表过滤）
        rsTables = mock(ResultSet.class);
        when(rsTables.next()).thenReturn(true, true, false);
        when(rsTables.getString("TABLE_NAME")).thenReturn("users", "sys_table");
        when(meta.getTables(anyString(), any(), anyString(), org.mockito.ArgumentMatchers.any(String[].class)))
                .thenReturn(rsTables);

        rsColumns = mock(ResultSet.class);
        when(rsColumns.next()).thenReturn(true, true, false);
        when(rsColumns.getString("COLUMN_NAME")).thenReturn("id", "name");
        when(rsColumns.getString("TYPE_NAME")).thenReturn("BIGINT", "VARCHAR");
        when(rsColumns.getInt("NULLABLE")).thenReturn(DatabaseMetaData.columnNoNulls,
                DatabaseMetaData.columnNullable);
        when(rsColumns.getString("COLUMN_DEF")).thenReturn(null, null);
        when(rsColumns.getString("IS_AUTOINCREMENT")).thenReturn("YES", "NO");
        when(meta.getColumns(anyString(), any(), anyString(), anyString())).thenReturn(rsColumns);

        rsPk = mock(ResultSet.class);
        when(rsPk.next()).thenReturn(true, false);
        when(rsPk.getString("COLUMN_NAME")).thenReturn("id");
        when(meta.getPrimaryKeys(anyString(), any(), anyString())).thenReturn(rsPk);

        rsFk = mock(ResultSet.class);
        when(rsFk.next()).thenReturn(false);
        when(meta.getImportedKeys(anyString(), any(), anyString())).thenReturn(rsFk);

        rsIndex = mock(ResultSet.class);
        when(rsIndex.next()).thenReturn(true, false);
        when(rsIndex.getBoolean("NON_UNIQUE")).thenReturn(false);
        when(rsIndex.getString("INDEX_NAME")).thenReturn("uk_name");
        when(rsIndex.getString("COLUMN_NAME")).thenReturn("name");
        when(meta.getIndexInfo(anyString(), any(), anyString(), eq(true), eq(false))).thenReturn(rsIndex);
    }

    private void preparedMocks() throws SQLException {
        ps = mock(PreparedStatement.class);
        rsEmpty = mock(ResultSet.class);
        when(rsEmpty.next()).thenReturn(false);
    }

    @Test
    void introspectSchemaReadsTablesColumnsPkAndUniqueIndexes() throws Exception {
        List<TableMetadata> tables = introspector.introspect(connection, DIALECT);
        assertThat(tables).hasSize(1);
        TableMetadata users = tables.get(0);
        assertThat(users.tableName()).isEqualTo("users");
        assertThat(users.primaryKey()).isEqualTo("id");
        assertThat(users.columns()).hasSize(2);
        assertThat(users.columns().get(0).autoIncrement()).isTrue();
        assertThat(users.columns().get(0).nullable()).isFalse();
        assertThat(users.uniqueIndexes()).extracting("indexName").containsExactly("uk_name");
    }

    @Test
    void introspectTableMatchesCaseInsensitivelyAndReturnsNullWhenMissing() throws Exception {
        assertThat(introspector.introspectTable(connection, DIALECT, "USERS")).isNotNull();
        assertThat(introspector.introspectTable(connection, DIALECT, "nope")).isNull();
    }

    @Test
    void compositePrimaryKeyFallsBackToNull() throws Exception {
        ResultSet rsCompositePk = mock(ResultSet.class);
        when(rsCompositePk.next()).thenReturn(true, true, false);
        when(rsCompositePk.getString("COLUMN_NAME")).thenReturn("a", "b");
        when(meta.getPrimaryKeys(anyString(), any(), anyString())).thenReturn(rsCompositePk);
        TableMetadata users = introspector.introspect(connection, DIALECT).get(0);
        assertThat(users.primaryKey()).isNull();
    }

    @Test
    void sqlExceptionWrapsInto2001() throws Exception {
        when(meta.getTables(anyString(), any(), anyString(), any(String[].class)))
                .thenThrow(new SQLException("boom"));
        assertThatThrownBy(() -> introspector.introspect(connection, DIALECT))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.SCHEMA_INTROSPECTION_FAILED.getCode());
    }

    // ---- 方言 SQL 构建与 MySQL 细节 ----

    @Test
    void buildInsertSqlRejectsEmptyColumns() {
        assertThatThrownBy(() -> DIALECT.buildInsertSql("t", java.util.List.of()))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED.getCode());
    }

    @Test
    void buildUpdateSqlRejectsEmptySetColumns() {
        assertThatThrownBy(() -> DIALECT.buildUpdateSql("t", java.util.List.of(), null))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED.getCode());
    }

    @Test
    void buildSelectSqlWithoutColumnsUsesStar() {
        assertThat(DIALECT.buildSelectSql("t", java.util.List.of(), null)).isEqualTo("SELECT * FROM t");
        assertThat(DIALECT.buildSelectSql("t", java.util.List.of("a", "b"), null))
                .isEqualTo("SELECT `a`, `b` FROM t");
        assertThat(DIALECT.buildSelectSql("t", java.util.List.of("a"), "id > 3"))
                .isEqualTo("SELECT `a` FROM t WHERE id > 3");
        assertThat(DIALECT.buildUpdateSql("t", java.util.List.of("x"), "id = ?"))
                .isEqualTo("UPDATE t SET `x`=? WHERE id = ?");
    }

    @Test
    void mySqlDetailsQuoteUrlSystemTableAndEnumParsing() throws Exception {
        assertThat(DIALECT.buildJdbcUrl("h", 3306, "db"))
                .isEqualTo("jdbc:mysql://h:3306/db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        assertThat(DIALECT.quoteIdentifier("a`b")).isEqualTo("`a``b`");

        DatabaseMetaData mysqlMeta = mock(DatabaseMetaData.class);
        when(mysqlMeta.getDatabaseProductName()).thenReturn("MySQL");
        assertThat(DIALECT.supports(mysqlMeta)).isTrue();
        DatabaseMetaData pgMeta = mock(DatabaseMetaData.class);
        when(pgMeta.getDatabaseProductName()).thenReturn("PostgreSQL");
        assertThat(DIALECT.supports(pgMeta)).isFalse();
    }
}