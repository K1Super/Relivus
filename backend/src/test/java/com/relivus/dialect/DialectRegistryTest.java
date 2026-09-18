package com.relivus.dialect;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 方言注册表测试（DOC-08 / DOC-01）。
 */
class DialectRegistryTest {

    private final DialectRegistry registry =
            new DialectRegistry(List.of(new MySQLDialect(), new PostgreSQLDialect()));

    @Test
    void resolvesByJdbcUrl() {
        assertThat(registry.resolveByJdbcUrl("jdbc:mysql://localhost:3306/db").name()).isEqualTo("mysql");
        assertThat(registry.resolveByJdbcUrl("jdbc:postgresql://localhost:5432/db").name()).isEqualTo("postgresql");
        assertThat(registry.resolveByJdbcUrl("JDBC:MYSQL:...").name()).isEqualTo("mysql");
    }

    @Test
    void rejectsUnsupportedOrNullJdbcUrl() {
        assertThatThrownBy(() -> registry.resolveByJdbcUrl("jdbc:oracle:thin:@host:1521:xe"))
                .isInstanceOf(UnsupportedDatabaseException.class)
                .hasFieldOrPropertyWithValue("code", com.relivus.common.exception.ErrorCode.UNSUPPORTED_DATABASE.getCode());
        assertThatThrownBy(() -> registry.resolveByJdbcUrl(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolvesByConnectionMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);

        when(metaData.getDatabaseProductName()).thenReturn("MySQL");
        assertThat(registry.resolve(connection).name()).isEqualTo("mysql");

        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        assertThat(registry.resolve(connection).name()).isEqualTo("postgresql");

        when(metaData.getDatabaseProductName()).thenReturn("Oracle");
        assertThatThrownBy(() -> registry.resolve(connection))
                .isInstanceOf(UnsupportedDatabaseException.class);
    }

    @Test
    void metaDataErrorWrapsAsIllegalState() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenThrow(new SQLException("boom"));
        assertThatThrownBy(() -> registry.resolve(connection))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exposesAllDialects() {
        assertThat(registry.all()).hasSize(2);
    }
}