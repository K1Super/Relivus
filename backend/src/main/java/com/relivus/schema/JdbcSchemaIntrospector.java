package com.relivus.schema;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dialect.DatabaseDialect;
import com.relivus.schema.model.TableMetadata;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.List;

/**
 * JDBC 内省实现（DOC-02）。
 *
 * <p>委托方言完成 JDBC 元数据读取，异常统一包装为错误码 2001。
 */
@Component
public class JdbcSchemaIntrospector implements SchemaIntrospector {

    @Override
    public List<TableMetadata> introspect(Connection connection, DatabaseDialect dialect) {
        try {
            return dialect.introspectSchema(connection);
        } catch (RelivusException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RelivusException(ErrorCode.SCHEMA_INTROSPECTION_FAILED,
                    "Schema introspection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public TableMetadata introspectTable(Connection connection, DatabaseDialect dialect, String tableName) {
        List<TableMetadata> tables = introspect(connection, dialect);
        for (TableMetadata table : tables) {
            if (table.tableName().equalsIgnoreCase(tableName)) {
                return table;
            }
        }
        return null;
    }
}