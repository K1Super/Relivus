package com.relivus.repository;

import com.relivus.entity.TaskEntity;
import com.relivus.task.TaskStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 统一任务表数据访问（df_task）。
 */
@Repository
public class TaskRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<TaskEntity> ROW_MAPPER = (rs, rowNum) -> {
        TaskEntity entity = new TaskEntity();
        entity.setId(rs.getLong("id"));
        entity.setTaskType(rs.getString("task_type"));
        entity.setConnectionId(rs.getObject("connection_id") == null ? null : rs.getLong("connection_id"));
        entity.setConfigJson(rs.getString("config_json"));
        entity.setStatus(TaskStatus.valueOf(rs.getString("status")));
        entity.setProgress(rs.getInt("progress"));
        entity.setTotalRows(rs.getLong("total_rows"));
        entity.setProcessedRows(rs.getLong("processed_rows"));
        entity.setErrorMessage(rs.getString("error_message"));
        entity.setCancelRequested(rs.getBoolean("cancel_requested"));
        entity.setDataBaselineJson(rs.getString("data_baseline_json"));
        if (rs.getTimestamp("created_at") != null) {
            entity.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        }
        if (rs.getTimestamp("started_at") != null) {
            entity.setStartedAt(rs.getTimestamp("started_at").toLocalDateTime());
        }
        if (rs.getTimestamp("finished_at") != null) {
            entity.setFinishedAt(rs.getTimestamp("finished_at").toLocalDateTime());
        }
        return entity;
    };

    public TaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TaskEntity> findAll(int limit, int offset) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        int safeOffset = Math.max(offset, 0);
        return jdbcTemplate.query(
                "SELECT * FROM df_task ORDER BY id DESC LIMIT ? OFFSET ?", ROW_MAPPER, safeLimit, safeOffset);
    }

    public Optional<TaskEntity> findById(Long id) {
        List<TaskEntity> result = jdbcTemplate.query("SELECT * FROM df_task WHERE id = ?", ROW_MAPPER, id);
        return result.stream().findFirst();
    }

    /** 插入并回填自增 ID（初始状态 PENDING）。 */
    public Long insert(TaskEntity entity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO df_task(task_type, connection_id, config_json, status, progress, "
                            + "total_rows, processed_rows, cancel_requested, data_baseline_json) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, entity.getTaskType());
            ps.setObject(2, entity.getConnectionId());
            ps.setString(3, entity.getConfigJson());
            ps.setString(4, entity.getStatus().name());
            ps.setInt(5, entity.getProgress());
            ps.setLong(6, entity.getTotalRows());
            ps.setLong(7, entity.getProcessedRows());
            ps.setBoolean(8, entity.isCancelRequested());
            ps.setString(9, entity.getDataBaselineJson());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    /** 全字段更新（状态流转专用，字段由调用方组装）。 */
    public int update(TaskEntity entity) {
        return jdbcTemplate.update(
                "UPDATE df_task SET status = ?, progress = ?, total_rows = ?, processed_rows = ?, "
                        + "error_message = ?, cancel_requested = ?, started_at = ?, finished_at = ? WHERE id = ?",
                entity.getStatus().name(), entity.getProgress(), entity.getTotalRows(), entity.getProcessedRows(),
                entity.getErrorMessage(), entity.isCancelRequested(),
                toTimestamp(entity.getStartedAt()), toTimestamp(entity.getFinishedAt()),
                entity.getId());
    }

    /** 增量更新进度与已处理行数（高频路径，避免整行覆盖）。 */
    public int updateProgress(Long id, int progress, long processedRows) {
        return jdbcTemplate.update(
                "UPDATE df_task SET progress = ?, processed_rows = ? WHERE id = ?",
                Math.max(0, Math.min(100, progress)), processedRows, id);
    }

    /** 状态流转（可同时写进度、错误信息与结束时间）。 */
    public int updateStatus(Long id, TaskStatus status, int progress, String errorMessage, LocalDateTime finishedAt) {
        return jdbcTemplate.update(
                "UPDATE df_task SET status = ?, progress = ?, error_message = ?, finished_at = ? WHERE id = ?",
                status.name(), Math.max(0, Math.min(100, progress)), errorMessage,
                toTimestamp(finishedAt), id);
    }

    /** 启动标记：RUNNING + 开始时间。 */
    public int markRunning(Long id, LocalDateTime startedAt) {
        return jdbcTemplate.update(
                "UPDATE df_task SET status = 'RUNNING', started_at = ? WHERE id = ?",
                toTimestamp(startedAt), id);
    }

    /** 取消持久化标志（cancel 时同步落库）。 */
    public int markCancelRequested(Long id) {
        return jdbcTemplate.update("UPDATE df_task SET cancel_requested = TRUE WHERE id = ?", id);
    }

    /** 读取取消持久化标志（引擎每批处理检查，与内存标志双保险）。 */
    public boolean isCancelRequested(Long id) {
        Boolean flag = jdbcTemplate.queryForObject(
                "SELECT cancel_requested FROM df_task WHERE id = ?", Boolean.class, id);
        return Boolean.TRUE.equals(flag);
    }

    /** 写入生成数据回看基线 JSON（任务开始执行前由生成流程写入）。 */
    public int updateDataBaseline(Long id, String dataBaselineJson) {
        return jdbcTemplate.update("UPDATE df_task SET data_baseline_json = ? WHERE id = ?",
                dataBaselineJson, id);
    }

    private static Timestamp toTimestamp(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }
}