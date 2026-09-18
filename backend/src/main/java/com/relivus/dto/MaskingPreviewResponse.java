package com.relivus.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 脱敏预览响应（POST /api/masking/preview）。
 *
 * @param rows 各列的脱敏前后样例（仅预览，不写映射库）
 */
@Schema(description = "脱敏预览响应")
public record MaskingPreviewResponse(
        @NotNull List<PreviewRow> rows) {

    /** 单行预览。 */
    @Schema(description = "脱敏预览行")
    public record PreviewRow(
            String table,
            String column,
            String original,
            String masked) {
    }
}