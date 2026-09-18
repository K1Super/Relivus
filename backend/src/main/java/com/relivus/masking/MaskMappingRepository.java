package com.relivus.masking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

/**
 * 脱敏映射表数据访问（DOC-11.6 并发写）。
 *
 * <p>元数据库表 {@code df_mask_mapping}，唯一键 {@code (column_group, original_hash)}。
 * 并发写统一采用「INSERT 忽略冲突 + 回读」：MySQL 用 {@code INSERT IGNORE}，
 * PostgreSQL 用 {@code ON CONFLICT DO NOTHING}，保证同一原始值跨线程/跨任务只落一条映射。
 */
public class MaskMappingRepository {

    private static final Logger LOG = LoggerFactory.getLogger(MaskMappingRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean postgres;

    public MaskMappingRepository(DataSource dataSource, String dialectName) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.postgres = "postgresql".equalsIgnoreCase(dialectName);
    }

    /** 按哈希回读已存在的掩码值，不存在返回 null。 */
    public String find(String columnGroup, String originalHash) {
        List<String> rows = jdbcTemplate.query(
                "SELECT masked_value FROM df_mask_mapping WHERE column_group = ? AND original_hash = ?",
                (rs, i) -> rs.getString(1), columnGroup, originalHash);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 写入映射；冲突时忽略并回读已存在值（保证并发与幂等）。 */
    public String saveIfAbsent(String columnGroup, String originalHash, String maskedValue,
                               String algorithm, int keyVersion) {
        String sql = postgres
                ? "INSERT INTO df_mask_mapping (column_group, original_hash, masked_value, algorithm, key_version)"
                + " VALUES (?, ?, ?, ?, ?) ON CONFLICT (column_group, original_hash) DO NOTHING"
                : "INSERT IGNORE INTO df_mask_mapping (column_group, original_hash, masked_value, algorithm, key_version)"
                + " VALUES (?, ?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql, columnGroup, originalHash, maskedValue, algorithm, keyVersion);
        } catch (RuntimeException e) {
            // 极端并发下仍可能撞唯一索引：放弃本次写入，由回读兜底
            LOG.warn("mask mapping insert ignored due to race condition, group={}", columnGroup);
        }
        return find(columnGroup, originalHash);
    }
}