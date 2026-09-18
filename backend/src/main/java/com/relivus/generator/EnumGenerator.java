package com.relivus.generator;

import com.relivus.schema.model.ColumnMetadata;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ENUM 生成器：从列的 ENUM 取值或 {@code values} 参数中选取。
 * 参数：values（覆盖）、mode（random / round_robin，默认 random）。
 */
public class EnumGenerator implements ValueGenerator {

    @Override
    public String name() {
        return "enum";
    }

    @Override
    public Object generate(GenerationContext context) {
        List<String> values = context.param("values", null);
        if (values == null || values.isEmpty()) {
            values = context.column().enumValues();
        }
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("enum: no values available for column " + context.column().columnName());
        }
        String mode = context.param("mode", "random");
        if ("round_robin".equalsIgnoreCase(mode)) {
            int index = (int) (context.rowIndex() % values.size());
            return values.get(index);
        }
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    @Override
    public boolean supports(ColumnMetadata column) {
        return column.isEnum();
    }
}