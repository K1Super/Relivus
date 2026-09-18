package com.relivus.generator;

import com.relivus.schema.model.ColumnMetadata;

/**
 * 序列生成器：1..N 递增（自增列回填场景由引擎另行处理，本生成器用于显式序列列）。
 */
public class SequenceGenerator implements ValueGenerator {

    private final long start;
    private long next;

    public SequenceGenerator() {
        this(1L);
    }

    public SequenceGenerator(long start) {
        this.start = start;
        this.next = start;
    }

    @Override
    public String name() {
        return "sequence";
    }

    @Override
    public Object generate(GenerationContext context) {
        Long configured = context.param("start", null);
        if (configured != null && next == start && next != configured) {
            next = configured;
        }
        return next++;
    }

    @Override
    public boolean supports(ColumnMetadata column) {
        return false;
    }
}