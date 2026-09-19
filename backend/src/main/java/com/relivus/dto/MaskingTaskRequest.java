package com.relivus.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 脱敏执行/预览配置（POST /api/masking/*）。
 *
 * @param connectionId 目标库连接 ID
 * @param tables       各表脱敏规则
 * @param batchSize    批量读取/更新大小，默认 1000
 * @param verifyTables 待验证 JOIN 一致性的表（脱敏前自动建快照）
 */
@Schema(description = "脱敏配置")
public record MaskingTaskRequest(
        @NotNull(message = "connectionId must not be null")
        Long connectionId,

        @NotEmpty(message = "tables must not be empty")
        List<TableRule> tables,

        @Schema(description = "批大小，默认 1000")
        Integer batchSize,

        @Schema(description = "JOIN 一致性验证目标（快照于脱敏前创建）")
        List<VerifyTableSpec> verifyTables) {

    public int batchSizeOrDefault() {
        return batchSize == null || batchSize <= 0 ? 1000 : batchSize;
    }

    /** 参与 JOIN 一致性验证的表（脱敏前采样快照，脱敏后由 /api/masking/verify 比对）。 */
    @Schema(description = "JOIN 一致性验证目标表")
    public record VerifyTableSpec(
            @NotBlank(message = "table must not be blank")
            String table,
            @NotBlank(message = "pkColumn must not be blank")
            String pkColumn,
            @NotBlank(message = "joinKeyColumn must not be blank")
            String joinKeyColumn,
            @Schema(description = "过滤条件（与脱敏规则一致）")
            String whereClause) {
    }

    /** 单表脱敏规则。 */
    @Schema(description = "单表脱敏规则")
    public record TableRule(
            @NotBlank(message = "table must not be blank")
            String table,

            @NotEmpty(message = "columns must not be empty")
            Map<String, ColumnRule> columns,

            @Schema(description = "过滤条件（仅限可信操作者，如 id > 100）")
            String where) {

        /** 单列脱敏规则。 */
        @Schema(description = "单列脱敏规则")
        public record ColumnRule(
                @Schema(description = "算法：fixed/regex/hmac/phone/id_card/bank_card/faker；缺省按列名启发式")
                String algorithm,
                @Schema(description = "算法参数")
                Map<String, String> params,
                @Schema(description = "映射分组；同组同原始值脱敏结果跨表一致；缺省按外键/同名列自动分组")
                String columnGroup,
                @Schema(description = "密钥版本（hmac 使用），默认 1")
                Integer keyVersion) {

            public int keyVersionOrDefault() {
                return keyVersion == null || keyVersion <= 0 ? 1 : keyVersion;
            }
        }
    }
}