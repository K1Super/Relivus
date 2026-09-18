package com.relivus.schema.model;

import java.util.List;

/**
 * 唯一索引元数据（DOC-02 领域模型）。
 *
 * @param indexName 索引名
 * @param columns   构成唯一约束的列（单列或复合）
 */
public record UniqueIndexMetadata(
        String indexName,
        List<String> columns) {
}