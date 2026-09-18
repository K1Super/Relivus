package com.relivus.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 依赖图响应（DOC-06 / GET /api/schema/{connId}/dependencies）。
 *
 * <p>order 为无环拓扑序（父表在前）；cycles 为检测到的强连通环（生成时按 DOC-11.3
 * 执行两阶段插入与可空校验）。
 */
@Schema(description = "表依赖关系")
public record DependencyGraphResponse(List<String> order, List<List<String>> cycles) {
}