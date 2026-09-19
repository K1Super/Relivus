package com.relivus.entity;

import java.time.LocalDateTime;

/**
 * 任务日志实体（对应元数据库表 df_task_log）。
 *
 * <p>task_id 直接引用 {@code df_task.id}。日志用于任务审计与失败排查。
 */
public class TaskLogEntity {

    private Long id;
    private Long taskId;
    /** INFO / WARN / ERROR（对应引擎 Severity）。 */
    private String level;
    private String message;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}