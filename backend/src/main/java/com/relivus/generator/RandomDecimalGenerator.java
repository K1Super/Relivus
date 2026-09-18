package com.relivus.generator;

import com.relivus.schema.model.ColumnMetadata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机小数生成器：[min, max) 区间，保留 scale 位小数。默认 0..10000，2 位小数。
 * 参数：min、max、scale。
 */
public class RandomDecimalGenerator implements ValueGenerator {

    @Override
    public String name() {
        return "random_decimal";
    }

    @Override
    public Object generate(GenerationContext context) {
        double min = context.param("min", 0.0);
        double max = context.param("max", 10_000.0);
        int scale = context.param("scale", 2);
        if (max <= min) {
            throw new IllegalArgumentException("random_decimal: max must be > min");
        }
        double value = ThreadLocalRandom.current().nextDouble(min, max);
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    @Override
    public boolean supports(ColumnMetadata column) {
        String t = column.dataType().toUpperCase();
        return t.contains("DECIMAL") || t.contains("NUMERIC") || t.contains("FLOAT")
                || t.contains("DOUBLE") || t.contains("REAL");
    }
}