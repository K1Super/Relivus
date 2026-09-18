package com.relivus.schema.model;

/**
 * CHECK 约束解析类型（DOC-02）。
 *
 * <p>仅解析 {@code IN (...)} 与 {@code col >= x AND col <= y} 两种形式；
 * 多列或复杂表达式一律标记 {@link #UNKNOWN}，不猜测语义。
 */
public enum CheckType {
    /** 允许值枚举。 */
    IN,
    /** 范围约束 [min, max]。 */
    RANGE,
    /** 无法解析或涉及多列，不参与生成过滤。 */
    UNKNOWN
}