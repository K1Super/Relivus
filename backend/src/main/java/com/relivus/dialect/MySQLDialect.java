package com.relivus.dialect;

import com.relivus.schema.model.ColumnMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MySQL 8.0 方言。
 *
 * <ul>
 *   <li>标识符反引号引用</li>
 *   <li>分页 {@code LIMIT offset, size}</li>
 *   <li>自增 {@code AUTO_INCREMENT}、布尔 {@code TINYINT(1)}、时间 {@code DATETIME}、二进制 {@code BLOB}</li>
 *   <li>ENUM 列级 {@code ENUM('A','B')}</li>
 *   <li>CHECK 取自 {@code information_schema.CHECK_CONSTRAINTS}</li>
 * </ul>
 */
@Component
public class MySQLDialect extends AbstractJdbcDialect {

    private static final Logger LOG = LoggerFactory.getLogger(MySQLDialect.class);
    private static final Pattern ENUM_TYPE = Pattern.compile("^enum\\s*\\((.*)\\)$", Pattern.CASE_INSENSITIVE);

    private static final DataTypeMapping MAPPING = new DataTypeMapping(
            "TINYINT(1)", "DATETIME", "BLOB", "AUTO_INCREMENT",
            "ENUM('A','B')");

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public boolean supports(DatabaseMetaData metaData) {
        try {
            String product = metaData.getDatabaseProductName();
            return product != null && product.toLowerCase().contains("mysql");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read database product name", e);
        }
    }

    @Override
    public String buildJdbcUrl(String host, int port, String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    @Override
    String catalog(Connection connection, DatabaseMetaData metaData) throws SQLException {
        return connection.getCatalog();
    }

    @Override
    String schema(Connection connection, DatabaseMetaData metaData) {
        return null;
    }

    @Override
    boolean isSystemTable(String tableName, String schema) {
        return tableName.startsWith("sys") || tableName.startsWith("innodb_")
                || tableName.startsWith("mysql") || tableName.startsWith("performance_schema")
                || tableName.startsWith("information_schema") || tableName.startsWith("ndbinfo");
    }

    @Override
    boolean isAutoIncrement(ResultSet columnRs) throws SQLException {
        String value = columnRs.getString("IS_AUTOINCREMENT");
        return "YES".equalsIgnoreCase(value);
    }

    @Override
    Map<String, List<String>> findEnumValues(Connection connection, String schema, String tableName) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        String sql = "SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_TYPE LIKE 'enum%'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, connection.getCatalog());
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Matcher m = ENUM_TYPE.matcher(rs.getString("COLUMN_TYPE"));
                    if (m.matches()) {
                        result.put(rs.getString("COLUMN_NAME"), parseEnumValues(m.group(1)));
                    }
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to introspect enum values for table={}, err={}", tableName, e.getMessage());
        }
        return result;
    }

    /** 解析 {@code 'A','B'} 为取值列表。 */
    static List<String> parseEnumValues(String expr) {
        List<String> values = new ArrayList<>();
        Matcher m = Pattern.compile("'((?:[^']|'')*)'").matcher(expr);
        while (m.find()) {
            values.add(m.group(1).replace("''", "'"));
        }
        return values;
    }

    @Override
    List<String[]> findCheckConstraints(Connection connection, String schema, String tableName) {
        List<String[]> result = new ArrayList<>();
        String sql = "SELECT CONSTRAINT_NAME, CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = ? AND TABLE_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, connection.getCatalog());
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new String[]{rs.getString("CONSTRAINT_NAME"), rs.getString("CHECK_CLAUSE")});
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to introspect check constraints for table={}, err={}", tableName, e.getMessage());
        }
        return result;
    }

    @Override
    public DataTypeMapping getTypeMapping() {
        return MAPPING;
    }

    @Override
    public PaginationSyntax getPaginationSyntax() {
        return PaginationSyntax.MYSQL;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}