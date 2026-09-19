package com.relivus.dto;

import com.relivus.schema.model.CheckConstraintMetadata;
import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.ForeignKeyMetadata;
import com.relivus.schema.model.TableMetadata;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 表详情（GET /api/schema/{connId}/tables/{table}）。
 */
@Schema(description = "表详情")
public record TableDetailResponse(
        String tableName,
        String primaryKey,
        List<ColumnDetail> columns,
        List<ForeignKeyDetail> foreignKeys,
        List<CheckDetail> checks) {

    @Schema(description = "列详情")
    public record ColumnDetail(String columnName, String dataType, boolean nullable,
                               String defaultValue, boolean autoIncrement, List<String> enumValues) {
    }

    @Schema(description = "外键详情")
    public record ForeignKeyDetail(String fkName, String columnName, String refTable,
                                   String refColumn, boolean nullable) {
    }

    @Schema(description = "CHECK 约束详情")
    public record CheckDetail(String constraintName, List<String> columns, String type,
                              List<String> allowedValues, BigDecimal minValue, BigDecimal maxValue) {
    }

    public static TableDetailResponse from(TableMetadata meta) {
        List<ColumnDetail> columns = meta.columns().stream()
                .map(c -> new ColumnDetail(c.columnName(), c.dataType(), c.nullable(),
                        c.defaultValue(), c.autoIncrement(), c.enumValues()))
                .toList();
        List<ForeignKeyDetail> fks = meta.foreignKeys().stream()
                .map(fk -> new ForeignKeyDetail(fk.fkName(), fk.columnName(), fk.refTable(),
                        fk.refColumn(), fk.nullable()))
                .toList();
        List<CheckDetail> checks = meta.checkConstraints().stream()
                .map(c -> new CheckDetail(c.constraintName(), c.columns(), c.type().name(),
                        c.allowedValues(), c.minValue(), c.maxValue()))
                .toList();
        return new TableDetailResponse(meta.tableName(), meta.primaryKey(), columns, fks, checks);
    }
}