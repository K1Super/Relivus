package com.relivus.repository;

import com.relivus.entity.ConnectionEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * 目标库连接数据访问（df_connection）。
 */
@Repository
public class ConnectionRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<ConnectionEntity> ROW_MAPPER = (rs, rowNum) -> {
        ConnectionEntity entity = new ConnectionEntity();
        entity.setId(rs.getLong("id"));
        entity.setName(rs.getString("name"));
        entity.setDbType(rs.getString("db_type"));
        entity.setHost(rs.getString("host"));
        entity.setPort(rs.getInt("port"));
        entity.setDatabaseName(rs.getString("database_name"));
        entity.setUsername(rs.getString("username"));
        entity.setPasswordCipher(rs.getString("password_cipher"));
        if (rs.getTimestamp("created_at") != null) {
            entity.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        }
        if (rs.getTimestamp("updated_at") != null) {
            entity.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        }
        return entity;
    };

    public ConnectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ConnectionEntity> findAll() {
        return jdbcTemplate.query("SELECT * FROM df_connection ORDER BY id", ROW_MAPPER);
    }

    public Optional<ConnectionEntity> findById(Long id) {
        List<ConnectionEntity> result =
                jdbcTemplate.query("SELECT * FROM df_connection WHERE id = ?", ROW_MAPPER, id);
        return result.stream().findFirst();
    }

    public Optional<ConnectionEntity> findByName(String name) {
        List<ConnectionEntity> result =
                jdbcTemplate.query("SELECT * FROM df_connection WHERE name = ?", ROW_MAPPER, name);
        return result.stream().findFirst();
    }

    public boolean existsByName(String name) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM df_connection WHERE name = ?", Integer.class, name);
        return count != null && count > 0;
    }

    /** 插入并回填自增 ID。 */
    public Long insert(ConnectionEntity entity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO df_connection(name, db_type, host, port, database_name, username, password_cipher) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, entity.getName());
            ps.setString(2, entity.getDbType());
            ps.setString(3, entity.getHost());
            ps.setInt(4, entity.getPort());
            ps.setString(5, entity.getDatabaseName());
            ps.setString(6, entity.getUsername());
            ps.setString(7, entity.getPasswordCipher());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public int update(ConnectionEntity entity) {
        return jdbcTemplate.update(
                "UPDATE df_connection SET name = ?, db_type = ?, host = ?, port = ?, "
                        + "database_name = ?, username = ?, password_cipher = ?, updated_at = ? WHERE id = ?",
                entity.getName(), entity.getDbType(), entity.getHost(), entity.getPort(),
                entity.getDatabaseName(), entity.getUsername(), entity.getPasswordCipher(),
                Timestamp.valueOf(entity.getUpdatedAt() != null ? entity.getUpdatedAt()
                        : java.time.LocalDateTime.now()),
                entity.getId());
    }

    public int delete(Long id) {
        return jdbcTemplate.update("DELETE FROM df_connection WHERE id = ?", id);
    }
}