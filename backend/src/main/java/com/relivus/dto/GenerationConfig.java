package com.relivus.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 生成执行/预览配置（DOC-03 / DOC-06）。
 *
 * @param connectionId     目标库连接 ID
 * @param tables           各表配置
 * @param samplingStrategy 外键采样策略，默认 UNIFORM
 * @param truncateBefore   生成前是否清空目标表，默认 false
 * @param batchSize        批量插入大小，默认 1000
 */
@Schema(description = "数据生成配置")
public record GenerationConfig(
        @NotNull(message = "connectionId must not be null")
        Long connectionId,

        @NotEmpty(message = "tables must not be empty")
        List<TableConfig> tables,

        @Schema(description = "采样策略：UNIFORM / ZIPF，默认 UNIFORM")
        String samplingStrategy,

        @Schema(description = "生成前清空目标表")
        Boolean truncateBefore,

        @Min(value = 1, message = "batchSize must be >= 1")
        @Schema(description = "批大小，默认 1000")
        Integer batchSize) {

    public String samplingStrategyOrDefault() {
        return samplingStrategy == null || samplingStrategy.isBlank() ? "UNIFORM" : samplingStrategy.toUpperCase();
    }

    public int batchSizeOrDefault() {
        return batchSize == null || batchSize <= 0 ? 1000 : batchSize;
    }

    public boolean truncateBeforeOrDefault() {
        return Boolean.TRUE.equals(truncateBefore);
    }

    /** 单表生成配置。 */
    @Schema(description = "单表生成配置")
    public record TableConfig(
            @NotBlank(message = "table must not be blank")
            String table,

            @NotNull(message = "rowCount must not be null")
            @Min(value = 1, message = "rowCount must be >= 1")
            @Max(value = 500_000, message = "rowCount must be <= 500000")
            Integer rowCount,

            @Schema(description = "列 -> 生成器配置")
            Map<String, ColumnConfig> columns) {

        /** 列生成配置。 */
        @Schema(description = "列生成器配置")
        public record ColumnConfig(
                @NotBlank(message = "generator must not be blank")
                String generator,
                @Schema(description = "生成器参数")
                Map<String, Object> params) {
        }
    }
}