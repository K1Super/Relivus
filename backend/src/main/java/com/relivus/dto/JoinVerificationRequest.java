package com.relivus.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * JOIN 一致性验证请求（POST /api/masking/verify）。
 *
 * @param connectionId  目标库连接 ID
 * @param joinSql       带主键占位符的 JOIN 查询，如
 *                      {@code SELECT oi.order_id FROM order o JOIN order_item oi ON o.id = oi.order_id WHERE o.id = ?}
 * @param targetTable   抽样源表（主键与 JOIN 键所在表）
 * @param pkColumn      抽样源表主键列
 * @param joinKeyColumn JOIN 连接键列（对比列）
 * @param whereClause   抽样过滤条件（可选，仅限可信操作者）
 */
@Schema(description = "JOIN 一致性验证请求")
public record JoinVerificationRequest(
        @NotNull(message = "connectionId must not be null")
        Long connectionId,

        @NotBlank(message = "joinSql must not be blank")
        String joinSql,

        @NotBlank(message = "targetTable must not be blank")
        String targetTable,

        @NotBlank(message = "pkColumn must not be blank")
        String pkColumn,

        @NotBlank(message = "joinKeyColumn must not be blank")
        String joinKeyColumn,

        @Schema(description = "抽样过滤条件")
        String whereClause) {
}