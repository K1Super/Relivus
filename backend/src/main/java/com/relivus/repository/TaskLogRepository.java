package com.relivus.repository;

import com.relivus.entity.TaskLogEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

/**
 * 任务日志数据访问（df_task_log，DOC-05）。
 */
@Repository
public class TaskLogRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<TaskLogEntity> ROW_MAPPER = (rs, rowNum) -> {
        TaskLogEntity entity = new TaskLogEntity();
        entity.setId(rs.getLong("id"));
        entity.setTaskId(rs.getLong("task_id"));
        entity.setLevel(rs.getString("level"));
        entity.setMessage(rs.getString("message"));
        if (rs.getTimestamp("created_at") != null) {
            entity.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        }
        return entity;
    };

    public TaskLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TaskLogEntity> findByTaskId(Long taskId) {
        return jdbcTemplate.query(
                "SELECT * FROM df_task_log WHERE task_id = ? ORDER BY id DESC LIMIT 500", ROW_MAPPER, taskId);
    }

    public Long insert(TaskLogEntity entity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO df_task_log(task_id, level, message) VALUES (?, ?, ?)",
                    new String[]{"id"});
            ps.setLong(1, entity.getTaskId());
            ps.setString(2, entity.getLevel());
            ps.setString(3, truncate(entity.getMessage()));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    /** 日志消息截断到 4000 字符，避免脏数据撑爆单行。 */
    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= 4000 ? message : message.substring(0, 4000);
    }

    /** 落库时间戳（RowMapper 反查时使用，实体无 createdAt 时可回填当前时间）。 */
    static Timestamp nowTimestamp() {
        return Timestamp.valueOf(java.time.LocalDateTime.now());
    }
}