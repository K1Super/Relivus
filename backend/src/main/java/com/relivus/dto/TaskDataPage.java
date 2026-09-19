package com.relivus.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 生成任务数据回看：分页数据页（GET /api/tasks/{id}/data）。
 *
 * @param table            表名
 * @param columns          列（顺序与 rows 内层数组一致）
 * @param rows             行数据（列值已统一归一化为 JSON 可序列化标量/列表）
 * @param total            满足筛选条件的总行数（用于前端分页）
 * @param limit            本页行数上限
 * @param offset           起始偏移
 * @param watermarkApplied 是否按「主键 > 基线」筛选本次生成行
 */
@Schema(description = "生成任务分页数据页")
public record TaskDataPage(
        String table,
        List<TaskDataColumn> columns,
        List<List<Object>> rows,
        long total,
        int limit,
        int offset,
        boolean watermarkApplied) {

    /** 列信息。 */
    @Schema(description = "列信息")
    public record TaskDataColumn(String name, String type) {
    }
}
