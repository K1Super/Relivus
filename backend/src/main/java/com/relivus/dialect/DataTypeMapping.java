package com.relivus.dialect;

/**
 * 类型映射（DOC-01 方言差异表）。
 *
 * <p>提供生成器与内省器需要的规范类型 → 各库 DDL 类型映射，覆盖布尔、时间、二进制、
 * 自增、ENUM 等差异点。
 */
public final class DataTypeMapping {

    private final String booleanType;
    private final String timestampType;
    private final String binaryType;
    private final String autoIncrementClause;
    private final String enumDdl;

    /** 仅允许同包内构造。 */
    DataTypeMapping(String booleanType, String timestampType, String binaryType,
                    String autoIncrementClause, String enumDdl) {
        this.booleanType = booleanType;
        this.timestampType = timestampType;
        this.binaryType = binaryType;
        this.autoIncrementClause = autoIncrementClause;
        this.enumDdl = enumDdl;
    }

    /** 布尔类型 DDL。 */
    public String booleanType() {
        return booleanType;
    }

    /** 时间类型 DDL。 */
    public String timestampType() {
        return timestampType;
    }

    /** 二进制类型 DDL。 */
    public String binaryType() {
        return binaryType;
    }

    /** 自增子句（列定义后缀）。 */
    public String autoIncrementClause() {
        return autoIncrementClause;
    }

    /** ENUM 列 DDL：MySQL 为 {@code ENUM(...)}，PG 返回 null 表示需独立 TYPE。 */
    public String enumDdl() {
        return enumDdl;
    }

    /** 判断规范类型对应是否布尔（含 MySQL TINYINT(1) 布尔惯例）。 */
    public boolean isBoolean(String jdbcType) {
        String t = jdbcType.toUpperCase();
        return t.contains("BOOL") || t.replace(" ", "").equals("TINYINT(1)");
    }

    /** 判断规范类型对应是否时间。 */
    public boolean isTemporal(String jdbcType) {
        String t = jdbcType.toUpperCase();
        return t.contains("DATE") || t.contains("TIME") || t.contains("TIMESTAMP");
    }

    /** 判断规范类型对应是否数值。 */
    public boolean isNumeric(String jdbcType) {
        String t = jdbcType.toUpperCase();
        return t.contains("INT") || t.contains("DECIMAL") || t.contains("NUMERIC")
                || t.contains("FLOAT") || t.contains("DOUBLE") || t.contains("REAL")
                || t.equals("BIGINT") || t.equals("SMALLINT") || t.equals("TINYINT");
    }
}