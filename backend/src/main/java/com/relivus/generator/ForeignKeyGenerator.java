package com.relivus.generator;

import com.relivus.schema.model.ColumnMetadata;

/**
 * 外键生成器：使用引擎注入的外键采样值（context 参数 {@code fk_value}）。
 *
 * <p>引擎在生成外键列时调用 {@link ForeignKeySampler} 采样父表主键，
 * 并将结果注入本生成器上下文，保证子表外键 100% 有效且不做全量加载。
 */
public class ForeignKeyGenerator implements ValueGenerator {

    public static final String FK_VALUE_PARAM = "fk_value";

    @Override
    public String name() {
        return "foreign_key";
    }

    @Override
    public Object generate(GenerationContext context) {
        Object value = context.param(FK_VALUE_PARAM, null);
        if (value == null) {
            throw new IllegalStateException(
                    "foreign_key: engine must provide '" + FK_VALUE_PARAM + "' for column "
                            + context.column().columnName() + ", table " + context.table());
        }
        return value;
    }

    @Override
    public boolean supports(ColumnMetadata column) {
        return false;
    }
}