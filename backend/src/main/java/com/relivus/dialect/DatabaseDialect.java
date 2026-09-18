package com.relivus.dialect;

import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.TableMetadata;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

/**
 * 数据库方言抽象（DOC-01）。
 *
 * <p>封装 MySQL/PostgreSQL 在 URL、标识符引用、分页、自增、类型映射、内省 SQL 上的差异。
 */
public interface DatabaseDialect {

    /** 方言名（如 mysql / postgresql）。 */
    String name();

    /** 是否支持该数据库产品（按 DatabaseMetaData.getDatabaseProductName 匹配）。 */
    boolean supports(DatabaseMetaData metaData);

    /** 构建 JDBC URL。 */
    String buildJdbcUrl(String host, int port, String database);

    /** 内省数据库 Schema（表、列、主键、外键、唯一、CHECK、ENUM）。 */
    List<TableMetadata> introspectSchema(Connection connection);

    /** 构建 INSERT SQL：{@code INSERT INTO t (c1, c2) VALUES (?, ?)}。 */
    String buildInsertSql(String table, List<ColumnMetadata> columns);

    /** 构建分页 SELECT SQL（列名由调用方加标识符引用）。 */
    String buildSelectSql(String table, List<String> columns, String whereClause);

    /** 构建 UPDATE SQL：{@code UPDATE t SET c1=? WHERE ...}。 */
    String buildUpdateSql(String table, List<String> setColumns, String whereClause);

    /** 获取类型映射。 */
    DataTypeMapping getTypeMapping();

    /** 获取分页语法。 */
    PaginationSyntax getPaginationSyntax();

    /** 标识符引用（保留字/大小写敏感场景确保安全）。 */
    String quoteIdentifier(String identifier);
}