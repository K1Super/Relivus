package com.relivus.repository;

import com.relivus.entity.AiConfigEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

/**
 * AI 模型配置数据访问（df_ai_config）。
 */
@Repository
public class AiConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<AiConfigEntity> ROW_MAPPER = (rs, rowNum) -> {
        AiConfigEntity entity = new AiConfigEntity();
        entity.setId(rs.getLong("id"));
        entity.setName(rs.getString("name"));
        entity.setBaseUrl(rs.getString("base_url"));
        entity.setApiKeyCipher(rs.getString("api_key_cipher"));
        entity.setModel(rs.getString("model"));
        // MySQL TINYINT(1) 与 PG BOOLEAN 均可读取为 Boolean
        entity.setActive(rs.getBoolean("active"));
        if (rs.getTimestamp("created_at") != null) {
            entity.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        }
        if (rs.getTimestamp("updated_at") != null) {
            entity.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        }
        return entity;
    };

    public AiConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AiConfigEntity> findAll() {
        return jdbcTemplate.query("SELECT * FROM df_ai_config ORDER BY id", ROW_MAPPER);
    }

    public Optional<AiConfigEntity> findById(Long id) {
        List<AiConfigEntity> result =
                jdbcTemplate.query("SELECT * FROM df_ai_config WHERE id = ?", ROW_MAPPER, id);
        return result.stream().findFirst();
    }

    public Optional<AiConfigEntity> findActive() {
        List<AiConfigEntity> result =
                jdbcTemplate.query("SELECT * FROM df_ai_config WHERE active = ? LIMIT 1", ROW_MAPPER, true);
        return result.stream().findFirst();
    }

    public boolean existsByName(String name) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM df_ai_config WHERE name = ?", Integer.class, name);
        return count != null && count > 0;
    }

    public boolean existsByNameExcludingId(String name, Long id) {
        if (id == null) {
            return existsByName(name);
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM df_ai_config WHERE name = ? AND id <> ?", Integer.class, name, id);
        return count != null && count > 0;
    }

    /** 插入并回填自增 ID（created_at/updated_at 由 DB 默认值填充）。 */
    public Long insert(AiConfigEntity entity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO df_ai_config(name, base_url, api_key_cipher, model, active) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, entity.getName());
            ps.setString(2, entity.getBaseUrl());
            ps.setString(3, entity.getApiKeyCipher());
            ps.setString(4, entity.getModel());
            ps.setBoolean(5, entity.isActive());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    /** 更新时间显式写入（MySQL/PG 双方言一致，PG 无 ON UPDATE 默认值）。 */
    public int update(AiConfigEntity entity) {
        return jdbcTemplate.update(
                "UPDATE df_ai_config SET name = ?, base_url = ?, api_key_cipher = ?, model = ?, active = ?, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                entity.getName(), entity.getBaseUrl(), entity.getApiKeyCipher(), entity.getModel(),
                entity.isActive(), entity.getId());
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM df_ai_config WHERE id = ?", id);
    }

    public int deactivateAll() {
        return jdbcTemplate.update("UPDATE df_ai_config SET active = false");
    }

    public int activateById(Long id) {
        return jdbcTemplate.update("UPDATE df_ai_config SET active = true WHERE id = ?", id);
    }
}