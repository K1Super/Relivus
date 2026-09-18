package com.relivus.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Schema 表摘要（DOC-06 / GET /api/schema/{connId}/tables）。
 */
@Schema(description = "表摘要")
public record SchemaTableSummary(String tableName, int columnCount, String primaryKey) {

    @Schema(description = "表列表")
    public record TableListResponse(List<SchemaTableSummary> tables) {
    }
}