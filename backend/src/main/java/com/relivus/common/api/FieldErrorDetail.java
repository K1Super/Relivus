package com.relivus.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 字段级校验/错误明细（随 {@code ApiResponse.fieldErrors} 返回）。
 *
 * @param field   出错字段名（JavaBean 属性名，可含嵌套路径，如 {@code tables[0].rowCount}）
 * @param message 该字段的错误提示
 */
@Schema(description = "字段级错误明细")
public record FieldErrorDetail(
        @Schema(description = "出错字段名") String field,
        @Schema(description = "字段错误提示") String message) {
}