package com.relivus.schema.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * CHECK 约束元数据（DOC-02 / DOC-11.4）。
 *
 * @param constraintName 约束名
 * @param columns        涉及列（多列表达式标记 UNKNOWN）
 * @param type           解析结果
 * @param allowedValues  IN 形式允许值
 * @param minValue       RANGE 下界
 * @param maxValue       RANGE 上界
 */
public record CheckConstraintMetadata(
        String constraintName,
        List<String> columns,
        CheckType type,
        List<String> allowedValues,
        BigDecimal minValue,
        BigDecimal maxValue) {

    /** UNKNOWN 标记：无法解析或涉及多列。 */
    public static CheckConstraintMetadata unknown(String constraintName, String definition) {
        return new CheckConstraintMetadata(constraintName, List.of(), CheckType.UNKNOWN, List.of(), null, null);
    }
}