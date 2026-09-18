package com.relivus.dialect;

import com.relivus.schema.model.CheckConstraintMetadata;
import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.ForeignKeyMetadata;
import com.relivus.schema.model.TableMetadata;
import com.relivus.schema.model.UniqueIndexMetadata;
import com.relivus.schema.parser.CheckConstraintParser;
import com.relivus.common.exception.RelivusException;
import com.relivus.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 基于 {@link DatabaseMetaData} 的通用内省骨架（DOC-01 / DOC-02）。
 *
 * <p>表、列、主键、外键、唯一索引的读取逻辑双库一致，由本类实现；CHECK 与 ENUM 的
 * 查询 SQL 因库而异，由子类提供 {@link #findCheckConstraints} 与 {@link #findEnumValues}。
 * 内省失败统一包装为错误码 2001。
 */
public abstract class AbstractJdbcDialect implements DatabaseDialect {

    protected static final Logger log = LoggerFactory.getLogger(AbstractJdbcDialect.class);

    private static final String[] TABLE_TYPES = {"TABLE"};

    @Override
    public List<TableMetadata> introspectSchema(Connection connection) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            String catalog = catalog(connection, meta);
            String schema = schema(connection, meta);

            Map<String, TableMetadata> tables = new LinkedHashMap<>();
            try (ResultSet rs = meta.getTables(catalog, schema, "%", TABLE_TYPES)) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (isSystemTable(name, schema)) {
                        continue;
                    }
                    tables.put(name, introspectTable(connection, meta, catalog, schema, name));
                }
            }
            return new ArrayList<>(tables.values());
        } catch (SQLException e) {
            throw new RelivusException(ErrorCode.SCHEMA_INTROSPECTION_FAILED,
                    "Schema introspection failed: " + e.getMessage(), e);
        }
    }

    private TableMetadata introspectTable(Connection connection, DatabaseMetaData meta,
                                          String catalog, String schema, String tableName) throws SQLException {
        List<ColumnMetadata> columns = readColumns(connection, meta, catalog, schema, tableName);
        Map<String, Boolean> nullableByColumn = new HashMap<>();
        for (ColumnMetadata c : columns) {
            nullableByColumn.put(c.columnName(), c.nullable());
        }

        List<ForeignKeyMetadata> foreignKeys = readForeignKeys(meta, catalog, schema, tableName, nullableByColumn);
        List<UniqueIndexMetadata> uniqueIndexes = readUniqueIndexes(meta, catalog, schema, tableName);
        List<CheckConstraintMetadata> checkConstraints = readCheckConstraints(connection, schema, tableName, columns);
        String primaryKey = readPrimaryKey(meta, catalog, schema, tableName);

        return new TableMetadata(tableName, columns, foreignKeys, uniqueIndexes, checkConstraints, primaryKey);
    }

    /** 主键列读取：单列主键返回列名，复合主键返回 null（两阶段回填仅支持单列主键）。 */
    private String readPrimaryKey(DatabaseMetaData meta, String catalog, String schema,
                                  String tableName) throws SQLException {
        String pkColumn = null;
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                String column = rs.getString("COLUMN_NAME");
                if (pkColumn != null && !pkColumn.equals(column)) {
                    return null; // 复合主键不支持
                }
                pkColumn = column;
            }
        }
        return pkColumn;
    }

    /** 目录（database）：MySQL 使用，PG 返回 null。 */
    abstract String catalog(Connection connection, DatabaseMetaData metaData) throws SQLException;

    /** Schema：PG 使用（public），MySQL 返回 null。 */
    abstract String schema(Connection connection, DatabaseMetaData metaData) throws SQLException;

    /** 系统表过滤。 */
    abstract boolean isSystemTable(String tableName, String schema);

    private List<ColumnMetadata> readColumns(Connection connection, DatabaseMetaData meta,
                                             String catalog, String schema, String tableName) throws SQLException {
        List<ColumnMetadata> columns = new ArrayList<>();
        Map<String, List<String>> enumValues = findEnumValues(connection, schema, tableName);
        Set<String> identityColumns = findIdentityColumns(connection, schema, tableName);
        try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String typeName = rs.getString("TYPE_NAME");
                boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                String defaultValue = rs.getString("COLUMN_DEF");
                boolean autoIncrement = identityColumns.contains(columnName) || isAutoIncrement(rs);
                List<String> enums = enumValues.getOrDefault(columnName, List.of());
                columns.add(new ColumnMetadata(columnName, typeName, nullable, defaultValue, autoIncrement, enums));
            }
        }
        return columns;
    }

    /** 自增判定：MySQL 取 IS_AUTOINCREMENT，PG 由 {@code findIdentityColumns} 补充。 */
    abstract boolean isAutoIncrement(ResultSet columnRs) throws SQLException;

    /**
     * 显式 IDENTITY 列名集合（PG 专属；MySQL 无 identity 概念返回空集。
     * PG 方言经 information_schema.columns.is_identity 查询，避免依赖
     * JDBC 结果集不提供的 IS_IDENTITY 列）。
     */
    Set<String> findIdentityColumns(Connection connection, String schema, String tableName) {
        return Collections.emptySet();
    }

    /** 读取 ENUM 列取值，非 ENUM 返回空集合。 */
    abstract Map<String, List<String>> findEnumValues(Connection connection, String schema, String tableName);

    private List<ForeignKeyMetadata> readForeignKeys(DatabaseMetaData meta, String catalog, String schema,
                                                     String tableName, Map<String, Boolean> nullableByColumn) throws SQLException {
        List<ForeignKeyMetadata> fks = new ArrayList<>();
        try (ResultSet rs = meta.getImportedKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                String fkColumn = rs.getString("FKCOLUMN_NAME");
                String pkTable = rs.getString("PKTABLE_NAME");
                String pkColumn = rs.getString("PKCOLUMN_NAME");
                boolean nullable = nullableByColumn.getOrDefault(fkColumn, true);
                fks.add(new ForeignKeyMetadata(fkName, fkColumn, pkTable, pkColumn, nullable));
            }
        }
        return fks;
    }

    private List<UniqueIndexMetadata> readUniqueIndexes(DatabaseMetaData meta, String catalog, String schema,
                                                        String tableName) throws SQLException {
        Map<String, List<String>> indexColumns = new LinkedHashMap<>();
        try (ResultSet rs = meta.getIndexInfo(catalog, schema, tableName, true, false)) {
            while (rs.next()) {
                boolean unique = !rs.getBoolean("NON_UNIQUE");
                if (!unique) {
                    continue;
                }
                String indexName = rs.getString("INDEX_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                if (indexName == null || columnName == null || "PRIMARY".equalsIgnoreCase(indexName)) {
                    continue;
                }
                indexColumns.computeIfAbsent(indexName, k -> new ArrayList<>()).add(columnName);
            }
        }
        List<UniqueIndexMetadata> result = new ArrayList<>();
        indexColumns.forEach((name, cols) -> result.add(new UniqueIndexMetadata(name, cols)));
        return result;
    }

    private List<CheckConstraintMetadata> readCheckConstraints(Connection connection, String schema,
                                                               String tableName, List<ColumnMetadata> columns) {
        List<CheckConstraintMetadata> result = new ArrayList<>();
        Set<String> columnNames = new LinkedHashSet<>();
        for (ColumnMetadata c : columns) {
            columnNames.add(c.columnName());
        }
        for (String[] row : findCheckConstraints(connection, schema, tableName)) {
            result.add(CheckConstraintParser.parse(row[0], row[1], columnNames));
        }
        return result;
    }

    /** 读取 CHECK 约束，返回 [约束名, 定义文本]。 */
    abstract List<String[]> findCheckConstraints(Connection connection, String schema, String tableName);

    // ---- SQL 构建 ----

    @Override
    public String buildInsertSql(String table, List<ColumnMetadata> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED, "INSERT requires at least one column");
        }
        StringBuilder sb = new StringBuilder("INSERT INTO ").append(table).append(" (");
        StringBuilder values = new StringBuilder(" VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
                values.append(", ");
            }
            sb.append(quoteIdentifier(columns.get(i).columnName()));
            values.append("?");
        }
        return sb.append(")").append(values).append(")").toString();
    }

    @Override
    public String buildSelectSql(String table, List<String> columns, String whereClause) {
        StringBuilder sb = new StringBuilder("SELECT ");
        if (columns == null || columns.isEmpty()) {
            sb.append("*");
        } else {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(quoteIdentifier(columns.get(i)));
            }
        }
        sb.append(" FROM ").append(table);
        if (whereClause != null && !whereClause.isBlank()) {
            sb.append(" WHERE ").append(whereClause);
        }
        return sb.toString();
    }

    @Override
    public String buildUpdateSql(String table, List<String> setColumns, String whereClause) {
        if (setColumns == null || setColumns.isEmpty()) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED, "UPDATE requires at least one set column");
        }
        StringBuilder sb = new StringBuilder("UPDATE ").append(table).append(" SET ");
        for (int i = 0; i < setColumns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(quoteIdentifier(setColumns.get(i))).append("=?");
        }
        if (whereClause != null && !whereClause.isBlank()) {
            sb.append(" WHERE ").append(whereClause);
        }
        return sb.toString();
    }
}