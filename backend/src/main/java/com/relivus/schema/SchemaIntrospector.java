package com.relivus.schema;

import com.relivus.dialect.DatabaseDialect;
import com.relivus.schema.model.TableMetadata;

import java.sql.Connection;
import java.util.List;

/**
 * Schema 内省接口（DOC-02）。
 *
 * <p>封装对目标数据库全部业务表的元数据读取，具体读取 SQL 由方言实现（DOC-01），
 * 本接口作为内省入口，便于缓存与测试 Mock。
 */
public interface SchemaIntrospector {

    /**
     * 内省目标库全部业务表元数据。
     *
     * @param connection 目标库连接
     * @param dialect    已解析的方言
     * @return 表元数据列表
     */
    List<TableMetadata> introspect(Connection connection, DatabaseDialect dialect);

    /**
     * 内省单表元数据。
     *
     * @return 表不存在返回 null
     */
    TableMetadata introspectTable(Connection connection, DatabaseDialect dialect, String tableName);
}