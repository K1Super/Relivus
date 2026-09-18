package com.relivus.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 连接测试结果。
 */
@Schema(description = "连接测试结果")
public record ConnectionTestResult(
        @Schema(description = "是否成功") boolean success,
        @Schema(description = "提示信息") String message,
        @Schema(description = "识别到的数据库产品") String product,
        @Schema(description = "数据库版本") String version) {
}