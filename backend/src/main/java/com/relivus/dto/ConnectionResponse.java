package com.relivus.dto;

import com.relivus.entity.ConnectionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 连接出参（不含任何密码信息）。
 */
@Schema(description = "连接信息")
public record ConnectionResponse(
        @Schema(description = "连接 ID") Long id,
        @Schema(description = "连接名称") String name,
        @Schema(description = "数据库类型") String dbType,
        @Schema(description = "主机") String host,
        @Schema(description = "端口") int port,
        @Schema(description = "数据库名") String database,
        @Schema(description = "用户名") String username,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt) {

    public static ConnectionResponse from(ConnectionEntity entity) {
        return new ConnectionResponse(
                entity.getId(), entity.getName(), entity.getDbType(), entity.getHost(),
                entity.getPort(), entity.getDatabaseName(), entity.getUsername(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}