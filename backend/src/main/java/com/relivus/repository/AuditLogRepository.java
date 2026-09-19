package com.relivus.repository;

import com.relivus.entity.ConnectionEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

/**
 * 本地操作审计日志（df_audit_log）。
 *
 * <p>记录关键操作（创建/删除连接、任务创建/取消等），单用户工具亦保留，便于追溯与备份。
 */
@Repository
public class AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入审计日志。
     *
     * @param action 操作标识（如 connection_create / task_cancel）
     * @param target 操作对象
     * @param detail 详情（敏感信息必须预脱敏）
     * @param result success / failure
     * @param traceId 链路 ID
     */
    public int insert(String action, String target, String detail, String result, String traceId) {
        return jdbcTemplate.update(
                "INSERT INTO df_audit_log(action, target, detail, result, trace_id) VALUES (?, ?, ?, ?, ?)",
                action, target, detail, result, traceId);
    }

    public int insert(String action, String target, String result) {
        return insert(action, target, null, result, null);
    }

    public int countByAction(String action, long sinceHours) {
        Timestamp since = Timestamp.valueOf(java.time.LocalDateTime.now().minusHours(sinceHours));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM df_audit_log WHERE action = ? AND created_at >= ?",
                Integer.class, action, since);
        return count == null ? 0 : count;
    }
}