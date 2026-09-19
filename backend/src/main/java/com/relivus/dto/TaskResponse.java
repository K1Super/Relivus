package com.relivus.dto;

import com.relivus.entity.TaskEntity;
import com.relivus.task.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 任务响应（GET /api/tasks）。
 *
 * <p>不返回 configJson（含连接级配置的序列化，前端无需回显），其余字段与 df_task 对齐。
 */
@Schema(description = "任务信息")
public record TaskResponse(
        Long id,
        String taskType,
        Long connectionId,
        TaskStatus status,
        int progress,
        long totalRows,
        long processedRows,
        String errorMessage,
        boolean cancelRequested,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {

    public static TaskResponse from(TaskEntity entity) {
        return new TaskResponse(entity.getId(), entity.getTaskType(), entity.getConnectionId(),
                entity.getStatus(), entity.getProgress(), entity.getTotalRows(), entity.getProcessedRows(),
                entity.getErrorMessage(), entity.isCancelRequested(),
                entity.getCreatedAt(), entity.getStartedAt(), entity.getFinishedAt());
    }
}