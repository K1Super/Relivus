package com.relivus.dialect;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

/**
 * 方言注册表（DOC-01）。
 *
 * <p>通过 {@code DatabaseMetaData.getDatabaseProductName()} 匹配方言；匹配失败抛
 * {@link UnsupportedDatabaseException}（错误码 1002）。
 */
@Component
public class DialectRegistry {

    private final List<DatabaseDialect> dialects;

    public DialectRegistry(List<DatabaseDialect> dialects) {
        this.dialects = List.copyOf(dialects);
    }

    /** 按连接元数据解析方言。 */
    public DatabaseDialect resolve(Connection connection) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            for (DatabaseDialect dialect : dialects) {
                if (dialect.supports(metaData)) {
                    return dialect;
                }
            }
            throw new UnsupportedDatabaseException(metaData.getDatabaseProductName());
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Failed to read database metadata", e);
        }
    }

    /** 按 JDBC URL 前缀匹配方言（用于连接创建阶段，无需真实连接）。 */
    public DatabaseDialect resolveByJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            throw new IllegalArgumentException("jdbcUrl must not be null");
        }
        if (jdbcUrl.toLowerCase().startsWith("jdbc:mysql:")) {
            return findByDialectName("mysql");
        }
        if (jdbcUrl.toLowerCase().startsWith("jdbc:postgresql:")) {
            return findByDialectName("postgresql");
        }
        throw new UnsupportedDatabaseException(jdbcUrl);
    }

    private DatabaseDialect findByDialectName(String name) {
        for (DatabaseDialect dialect : dialects) {
            if (dialect.name().equalsIgnoreCase(name)) {
                return dialect;
            }
        }
        throw new IllegalStateException("No dialect registered for name: " + name);
    }

    public List<DatabaseDialect> all() {
        return dialects;
    }
}