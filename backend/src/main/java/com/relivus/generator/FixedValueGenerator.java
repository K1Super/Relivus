package com.relivus.generator;

import com.relivus.schema.model.ColumnMetadata;

/**
 * 固定值生成器：所有行输出同一值。
 * 参数：value（必填）。
 */
public class FixedValueGenerator implements ValueGenerator {

    @Override
    public String name() {
        return "fixed";
    }

    @Override
    public Object generate(GenerationContext context) {
        Object value = context.param("value", null);
        if (value == null) {
            throw new IllegalArgumentException("fixed: param 'value' is required");
        }
        return value;
    }

    @Override
    public boolean supports(ColumnMetadata column) {
        return false;
    }
}