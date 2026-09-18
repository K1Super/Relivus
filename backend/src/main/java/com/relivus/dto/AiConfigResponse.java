package com.relivus.dto;

import com.relivus.entity.AiConfigEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * AI 配置出参（不含任何密钥信息）。
 */
@Schema(description = "AI 配置信息")
public record AiConfigResponse(
        @Schema(description = "配置 ID") long id,
        @Schema(description = "配置名称") String name,
        @Schema(description = "OpenAI 兼容接口基址") String baseUrl,
        @Schema(description = "模型名") String model,
        @Schema(description = "是否激活") boolean active,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt) {

    public static AiConfigResponse from(AiConfigEntity entity) {
        return new AiConfigResponse(
                entity.getId(), entity.getName(), entity.getBaseUrl(), entity.getModel(),
                entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}