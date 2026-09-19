package com.relivus.schema.model;

import java.util.List;

/**
 * 列元数据（领域模型）。
 *
 * @param columnName    列名
 * @param dataType      JDBC 数据类型名（大写）
 * @param nullable      是否可空
 * @param defaultValue  默认值，无则 null
 * @param autoIncrement 是否自增
 * @param enumValues    ENUM 可选值，非 ENUM 列返回空列表
 */
public record ColumnMetadata(
        String columnName,
        String dataType,
        boolean nullable,
        String defaultValue,
        boolean autoIncrement,
        List<String> enumValues) {

    /** 是否 ENUM 列。 */
    public boolean isEnum() {
        return enumValues != null && !enumValues.isEmpty();
    }
}