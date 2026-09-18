package com.relivus.generator;

import java.util.Map;

/**
 * 生成运行结果（DOC-03 / DOC-05）。
 *
 * @param rowsByTable  各表实际插入行数
 * @param elapsedMillis 总耗时（毫秒）
 */
public record GenerationRunResult(
        Map<String, Long> rowsByTable,
        long elapsedMillis) {
}