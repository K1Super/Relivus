package com.relivus.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 生成任务数据回看：单表摘要（DOC-06 / GET /api/tasks/{id}/tables）。
 *
 * @param table           表名（来自任务配置白名单）
 * @param rowCount        该表本次配置的生成行数
 * @param watermarkApplied 是否可按「主键 > 基线」精确筛选本次生成行（false 表示无数值主键，回看全表）
 */
@Schema(description = "生成任务单表摘要")
public record TaskGeneratedTable(
        String table,
        int rowCount,
        boolean watermarkApplied) {
}
