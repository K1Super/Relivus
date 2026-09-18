package com.relivus.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建/更新目标库连接请求。
 *
 * @param name     连接名称（唯一）
 * @param dbType   数据库类型：mysql / postgresql
 * @param host     主机
 * @param port     端口
 * @param database 数据库名
 * @param username 用户名
 * @param password 密码（仅入参；存储为 AES-GCM 密文，出参永不回传）
 * @param updatePassword 是否为更新场景显式重设密码
 */
@Schema(description = "连接请求")
public record CreateConnectionRequest(
        @NotBlank(message = "connection name must not be blank")
        @Size(max = 128, message = "connection name too long")
        @Schema(description = "连接名称，唯一") String name,

        @NotBlank(message = "db type must not be blank")
        @Schema(description = "mysql / postgresql") String dbType,

        @NotBlank(message = "host must not be blank")
        @Size(max = 255) @Schema(description = "主机地址") String host,

        @NotNull(message = "port must not be null")
        @Min(value = 1, message = "port must be >= 1")
        @Max(value = 65535, message = "port must be <= 65535")
        @Schema(description = "端口") Integer port,

        @NotBlank(message = "database must not be blank")
        @Size(max = 128) @Schema(description = "数据库名") String database,

        @NotBlank(message = "username must not be blank")
        @Size(max = 128) @Schema(description = "用户名") String username,

        @Size(max = 1024) @Schema(description = "密码，仅入参") String password,

        @Schema(description = "更新时是否重设密码") Boolean updatePassword) {

    /** 是否携带新密码（创建必填，更新可选）。 */
    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    public boolean isUpdatePassword() {
        return Boolean.TRUE.equals(updatePassword);
    }
}