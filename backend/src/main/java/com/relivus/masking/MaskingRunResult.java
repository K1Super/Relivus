package com.relivus.masking;

import java.util.Map;

/**
 * 脱敏运行结果（DOC-05）。
 *
 * @param rowsByTable   各表实际处理行数
 * @param elapsedMillis 总耗时（毫秒）
 */
public record MaskingRunResult(
        Map<String, Long> rowsByTable,
        long elapsedMillis) {
}