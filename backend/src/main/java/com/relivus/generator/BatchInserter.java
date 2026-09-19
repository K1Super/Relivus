package com.relivus.generator;

import com.relivus.dialect.DatabaseDialect;
import com.relivus.schema.model.ColumnMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 批量插入器（主键回填）。
 *
 * <p>父表插入使用 {@link GeneratedKeyHolder} 分批回填自增主键，子表外键引用回填 ID。
 * SQL 由方言构建，批大小可配（默认 1000）。
 */
public class BatchInserter {

    private static final Logger LOG = LoggerFactory.getLogger(BatchInserter.class);

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseDialect dialect;

    public BatchInserter(JdbcTemplate jdbcTemplate, DatabaseDialect dialect) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = dialect;
    }

    /**
     * 批量插入并回填自增主键。
     *
     * @return ID 列表，与 rows 顺序一致
     */
    public List<Long> insertReturningIds(String table, List<ColumnMetadata> columns,
                                         List<Object[]> rows, int batchSize) {
        List<Long> ids = new ArrayList<>(rows.size());
        String sql = dialect.buildInsertSql(table, columns);
        // PG 驱动在 RETURN_GENERATED_KEYS 下返回全部列，导致 GeneratedKeyHolder 报 multi-key；
        // 显式指定自增列名（双库兼容），无自增列时退化回驱动默认行为。
        String[] generatedColumns = columns.stream()
                .filter(ColumnMetadata::autoIncrement)
                .findFirst()
                .map(c -> new String[]{c.columnName()})
                .orElse(null);
        // PostgreSQL 批量 INSERT ... RETURNING 的 keys 返回顺序不保证与 VALUES 顺序一致，
        // 会导致主键回填错位（如 created_at 与 id 非单调）。PG 下逐行插入取 key 保证与 rows 顺序一致。
        if ("postgresql".equals(dialect.name())) {
            for (Object[] row : rows) {
                KeyHolder keyHolder = new GeneratedKeyHolder();
                jdbcTemplate.update(connection -> {
                    PreparedStatement ps = generatedColumns != null
                            ? connection.prepareStatement(sql, generatedColumns)
                            : connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int c = 0; c < row.length; c++) {
                        ps.setObject(c + 1, row[c]);
                    }
                    return ps;
                }, keyHolder);
                List<Long> one = extractIds(keyHolder, 1);
                ids.add(one.isEmpty() ? null : one.get(0));
            }
            return ids;
        }
        for (int i = 0; i < rows.size(); i += batchSize) {
            List<Object[]> batch = rows.subList(i, Math.min(i + batchSize, rows.size()));
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.batchUpdate(connection -> generatedColumns != null
                    ? connection.prepareStatement(sql, generatedColumns)
                    : connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS),
                    new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int j) throws SQLException {
                    Object[] row = batch.get(j);
                    for (int c = 0; c < row.length; c++) {
                        ps.setObject(c + 1, row[c]);
                    }
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            }, keyHolder);
            List<Long> batchIds = extractIds(keyHolder, batch.size());
            ids.addAll(batchIds);
            LOG.debug("insertReturningIds table={}, batch={}, keys={}", table, batch.size(), batchIds.size());
        }
        return ids;
    }

    /** 从 KeyHolder 提取主键；驱动只返回末尾一行时，按批内行数补 null 保持顺序对齐。 */
    private static List<Long> extractIds(KeyHolder keyHolder, int rowCount) {
        List<Long> batchIds = new ArrayList<>(rowCount);
        for (Map<String, Object> keys : keyHolder.getKeyList()) {
            for (Map.Entry<String, Object> entry : keys.entrySet()) {
                if (isIdColumn(entry.getKey()) && entry.getValue() instanceof Number number) {
                    batchIds.add(number.longValue());
                }
            }
        }
        while (batchIds.size() < rowCount) {
            batchIds.add(null);
        }
        return batchIds;
    }

    /**
     * 无主键回填需求的批量插入（如已显式生成主键、无自增列的表）。
     */
    public void insertBulk(String table, List<ColumnMetadata> columns, List<Object[]> rows, int batchSize) {
        String sql = dialect.buildInsertSql(table, columns);
        for (int i = 0; i < rows.size(); i += batchSize) {
            List<Object[]> batch = rows.subList(i, Math.min(i + batchSize, rows.size()));
            jdbcTemplate.batchUpdate(sql, batch);
        }
    }

    private static boolean isIdColumn(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.equals("id") || lower.equals("generated_keys");
    }
}