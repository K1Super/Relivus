package com.relivus.entity;

import com.relivus.task.TaskStatus;

import java.time.LocalDateTime;

/**
 * 统一任务实体（DOC-05 对应元数据库表 df_task）。
 *
 * <p>生成与脱敏任务共用一张表；{@code configJson} 保存任务配置（GenerationConfig / MaskingTaskRequest
 * 的 JSON），由执行层反序列化。{@code cancelRequested} 为取消持久化标志（DOC-11.7 P0 补丁），
 * 引擎每批处理同时检查内存标志与该列。
 */
public class TaskEntity {

    private Long id;
    /** GENERATION / MASKING。 */
    private String taskType;
    private Long connectionId;
    private String configJson;
    private TaskStatus status;
    private int progress; // 0-100
    private long totalRows;
    private long processedRows;
    private String errorMessage;
    private boolean cancelRequested;
    /** 生成任务各表基线 MAX(主键) JSON（DOC-06 数据回看；null 表示无数值主键，回看全表）。 */
    private String dataBaselineJson;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public Long getConnectionId() { return connectionId; }
    public void setConnectionId(Long connectionId) { this.connectionId = connectionId; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public long getTotalRows() { return totalRows; }
    public void setTotalRows(long totalRows) { this.totalRows = totalRows; }
    public long getProcessedRows() { return processedRows; }
    public void setProcessedRows(long processedRows) { this.processedRows = processedRows; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public boolean isCancelRequested() { return cancelRequested; }
    public void setCancelRequested(boolean cancelRequested) { this.cancelRequested = cancelRequested; }
    public String getDataBaselineJson() { return dataBaselineJson; }
    public void setDataBaselineJson(String dataBaselineJson) { this.dataBaselineJson = dataBaselineJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}