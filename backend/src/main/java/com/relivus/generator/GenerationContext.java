package com.relivus.generator;

import com.relivus.dialect.DatabaseDialect;
import com.relivus.schema.model.ColumnMetadata;

import java.util.Map;

/**
 * 生成上下文（DOC-03）。
 *
 * @param table    当前表名
 * @param column   当前列
 * @param rowIndex 行号（0 起）
 * @param totalRows 本次生成总行数
 * @param params   用户为该方法配置的参数（来自请求 config）
 * @param dialect  目标库方言
 */
public record GenerationContext(
        String table,
        ColumnMetadata column,
        long rowIndex,
        long totalRows,
        Map<String, Object> params,
        DatabaseDialect dialect) {

    /** 读取配置参数，缺省返回默认值。 */
    @SuppressWarnings("unchecked")
    public <T> T param(String key, T defaultValue) {
        Object value = params == null ? null : params.get(key);
        return value == null ? defaultValue : (T) value;
    }
}