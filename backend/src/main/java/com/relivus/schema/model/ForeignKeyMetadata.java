package com.relivus.schema.model;

/**
 * 外键元数据（DOC-02 领域模型）。
 *
 * @param fkName    外键约束名
 * @param columnName 本表外键列
 * @param refTable   引用表
 * @param refColumn  引用列（通常为主键）
 * @param nullable   外键列是否可空（循环依赖判定依据）
 */
public record ForeignKeyMetadata(
        String fkName,
        String columnName,
        String refTable,
        String refColumn,
        boolean nullable) {

    /** 兼容旧构造（nullable 由调用方补查时使用）。 */
    public ForeignKeyMetadata(String fkName, String columnName, String refTable, String refColumn) {
        this(fkName, columnName, refTable, refColumn, true);
    }
}