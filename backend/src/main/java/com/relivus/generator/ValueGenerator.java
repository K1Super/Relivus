package com.relivus.generator;

import com.relivus.schema.model.ColumnMetadata;

/**
 * 值生成器 SPI。
 *
 * <p>按列类型 / 列名启发式 / 用户显式配置选择实现。实现必须线程安全或无共享可变状态。
 */
public interface ValueGenerator {

    /** 生成器注册名（配置与日志使用）。 */
    String name();

    /** 生成单列值。 */
    Object generate(GenerationContext context);

    /** 是否支持该列（用于按类型自动匹配）。 */
    boolean supports(ColumnMetadata column);
}