package com.relivus.generator;

import com.relivus.schema.model.ColumnMetadata;

import java.sql.Timestamp;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 时间生成器：过去时间段内、随行号单调递增的时间（列名启发式：created_at/updated_at 等）。
 * 参数：secondsAgo 最大回看秒数（默认 5 年）。
 *
 * <p>数据质量整改：按行号在回看窗口内线性推进并叠加小幅抖动，保证表内时间随行号近似单调、
 * 永不产生未来时间，消除同表时间顺序错乱。
 *
 * <p>返回 {@link Timestamp} 而非格式化字符串，确保 JDBC 以时间类型参数绑定
 * （PG 对 timestamp 列绑定 varchar 会报类型不符；STRING 值在双库下均可能出问题）。
 */
public class TimestampGenerator implements ValueGenerator {

    private static final long FIVE_YEARS_SECONDS = 5L * 365 * 24 * 3600;

    @Override
    public String name() {
        return "timestamp";
    }

    @Override
    public Object generate(GenerationContext context) {
        long secondsAgo = context.param("seconds_ago", FIVE_YEARS_SECONDS);
        long now = System.currentTimeMillis();
        long total = Math.max(1, context.totalRows());
        long base = now - Math.max(0, secondsAgo) * 1000L;
        long step = Math.max(0, secondsAgo) * 1000L / total;
        // 抖动限制在 1/8 步长内，保持近似单调且永不越界
        long jitter = ThreadLocalRandom.current().nextLong(-step / 8, step / 8 + 1);
        long target = Math.min(now, Math.max(base, base + step * context.rowIndex() + jitter));
        return new Timestamp(target);
    }

    @Override
    public boolean supports(ColumnMetadata column) {
        String t = column.dataType().toUpperCase();
        return t.contains("DATE") || t.contains("TIME") || t.contains("TIMESTAMP");
    }
}