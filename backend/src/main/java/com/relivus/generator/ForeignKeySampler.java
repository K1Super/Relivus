package com.relivus.generator;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dialect.DatabaseDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 外键采样器（DOC-03 / P0 补丁）。
 *
 * <p>禁止全量加载父表主键：先 COUNT，再按随机偏移取单行；ZIPF 策略把偏移向小序号倾斜。
 * 支持在已有数据中采样（父表有存量数据时保证外键有效）。
 */
public class ForeignKeySampler {

    private static final Logger LOG = LoggerFactory.getLogger(ForeignKeySampler.class);

    private final DataSource targetDataSource;
    private final DatabaseDialect dialect;

    public ForeignKeySampler(DataSource targetDataSource, DatabaseDialect dialect) {
        this.targetDataSource = targetDataSource;
        this.dialect = dialect;
    }

    /**
     * 采样一个父表主键值。
     *
     * @param table    父表
     * @param column   父表主键列
     * @param rowIndex 当前子行号（ZIPF 参考）
     */
    public Object sample(String table, String column, long rowIndex, SamplingStrategy strategy) {
        JdbcTemplate template = new JdbcTemplate(targetDataSource);
        String fkColumnRef = dialect.quoteIdentifier(column);
        Long count = template.queryForObject(
                "SELECT COUNT(*) FROM " + table, Long.class);
        if (count == null || count == 0) {
            throw new RelivusException(ErrorCode.CONNECTION_FAILED,
                    "Parent table " + table + " is empty, cannot sample FK");
        }
        long offset = pickOffset(count, rowIndex, strategy);
        String sql = dialect.getPaginationSyntax().apply(
                dialect.buildSelectSql(table, List.of(column), null), offset, 1);
        List<Object> row = template.queryForList(sql, Object.class);
        if (row.isEmpty()) {
            LOG.warn("FK sample returned empty page, table={}, offset={}", table, offset);
            return offsetValueFallback(table, column, template);
        }
        return row.get(0);
    }

    private long pickOffset(long total, long rowIndex, SamplingStrategy strategy) {
        if (total <= 1) {
            return 0;
        }
        return switch (strategy) {
            case UNIFORM -> ThreadLocalRandom.current().nextLong(total);
            case ZIPF -> {
                double skew = 1.0 / (1.0 + Math.min(rowIndex, 100L));
                long biased = (long) (Math.pow(ThreadLocalRandom.current().nextDouble(), skew) * total);
                yield Math.min(biased, total - 1);
            }
        };
    }

    /** 兜底：LIMIT 1 取首行主键（表刚插入数据场景）。 */
    private Object offsetValueFallback(String table, String column, JdbcTemplate template) {
        String sql = dialect.getPaginationSyntax().apply(
                dialect.buildSelectSql(table, List.of(column), null), 0, 1);
        List<Object> row = template.queryForList(sql, Object.class);
        if (row.isEmpty()) {
            throw new RelivusException(ErrorCode.CONNECTION_FAILED,
                    "Parent table " + table + " has no rows to sample FK");
        }
        return row.get(0);
    }
}