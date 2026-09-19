package com.relivus.schema.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 表元数据（领域模型）。
 *
 * @param tableName       表名
 * @param columns         列
 * @param foreignKeys     外键
 * @param uniqueIndexes   唯一索引
 * @param checkConstraints CHECK 约束
 * @param primaryKey      主键列名，无主键为 null（循环依赖两阶段回填定位行）
 */
public record TableMetadata(
        String tableName,
        List<ColumnMetadata> columns,
        List<ForeignKeyMetadata> foreignKeys,
        List<UniqueIndexMetadata> uniqueIndexes,
        List<CheckConstraintMetadata> checkConstraints,
        String primaryKey) {

    /** 按列名找列，不存在返回 null。 */
    public ColumnMetadata column(String columnName) {
        for (ColumnMetadata column : columns) {
            if (column.columnName().equals(columnName)) {
                return column;
            }
        }
        return null;
    }

    /** 主键列元数据，无主键返回 null。 */
    public ColumnMetadata primaryKeyColumn() {
        return primaryKey == null ? null : column(primaryKey);
    }
}