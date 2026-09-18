package com.relivus.generator;

import com.relivus.schema.model.ColumnMetadata;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机整数生成器：[min, max] 闭区间，默认 0..Integer.MAX_VALUE。
 * 参数：min、max。
 */
public class RandomIntGenerator implements ValueGenerator {

    private final long min;
    private final long max;

    public RandomIntGenerator() {
        this(0, Integer.MAX_VALUE);
    }

    public RandomIntGenerator(long min, long max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public String name() {
        return "random_int";
    }

    @Override
    public Object generate(GenerationContext context) {
        long lo = context.param("min", min);
        long hi = context.param("max", max);
        if (hi < lo) {
            throw new IllegalArgumentException("random_int: max must be >= min");
        }
        return ThreadLocalRandom.current().nextLong(lo, hi + 1);
    }

    @Override
    public boolean supports(ColumnMetadata column) {
        String t = column.dataType().toUpperCase();
        return t.contains("INT");
    }
}