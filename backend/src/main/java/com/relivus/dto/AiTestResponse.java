package com.relivus.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 连通性测试结果。
 *
 * @param reachable 是否可达
 * @param detail    模型回复摘要或错误详情
 */
@Schema(description = "AI 连通性测试结果")
public record AiTestResponse(
        @Schema(description = "是否可达") boolean reachable,
        @Schema(description = "摘要/详情") String detail) {
}