-- ============================================================
-- Relivus 元库初始化（MySQL 方言）——初始结构
-- 说明：目标库绝不执行本脚本；仅 Relivus 自有库（relivus_meta）使用。
-- ============================================================

-- 目标库连接（密码为 AES-GCM 密文：Base64(IV + cipherText + tag)，绝无明文）
CREATE TABLE df_connection (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(64)  NOT NULL,
    db_type         VARCHAR(16)  NOT NULL COMMENT 'mysql / postgresql',
    host            VARCHAR(128) NOT NULL,
    port            INT          NOT NULL,
    database_name   VARCHAR(64)  NOT NULL,
    username        VARCHAR(64)  NOT NULL,
    password_cipher VARCHAR(512) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_connection_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目标库连接';

-- 统一任务表（生成/脱敏共用）
CREATE TABLE df_task (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    task_type       VARCHAR(32)  NOT NULL COMMENT 'GENERATION / MASKING',
    connection_id   BIGINT       NULL COMMENT '目标库连接 ID',
    config_json     TEXT         NULL COMMENT '任务配置 JSON',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
    progress        INT          NOT NULL DEFAULT 0 COMMENT '0-100',
    total_rows      BIGINT       NOT NULL DEFAULT 0,
    processed_rows  BIGINT       NOT NULL DEFAULT 0,
    error_message   TEXT         NULL,
    cancel_requested BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '取消持久化标志',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at      TIMESTAMP    NULL,
    finished_at     TIMESTAMP    NULL,
    PRIMARY KEY (id),
    KEY idx_task_status (status),
    KEY idx_task_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一任务表';

-- 任务日志
CREATE TABLE df_task_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    task_id     BIGINT       NOT NULL,
    level       VARCHAR(16)  NOT NULL COMMENT 'INFO/WARN/ERROR',
    message     TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task_log_task_id (task_id),
    CONSTRAINT fk_task_log_task FOREIGN KEY (task_id) REFERENCES df_task (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务日志';

-- 脱敏映射表（同组同原始值跨表保持一致，唯一键防并发重复写）
CREATE TABLE df_mask_mapping (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    column_group   VARCHAR(255) NOT NULL,
    original_hash  VARCHAR(64)  NOT NULL COMMENT 'SHA-256 十六进制',
    masked_value   TEXT         NOT NULL,
    algorithm      VARCHAR(64)  NOT NULL,
    key_version    INT          NOT NULL DEFAULT 1,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mask_group_hash (column_group, original_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脱敏映射表';

-- 操作审计
CREATE TABLE df_audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    action      VARCHAR(64)  NOT NULL COMMENT 'connection_create / generation_execute 等',
    target      VARCHAR(255) NULL,
    detail      TEXT         NULL COMMENT '敏感信息必须已脱敏',
    result      VARCHAR(16)  NOT NULL DEFAULT 'success',
    trace_id    VARCHAR(64)  NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_action (action),
    KEY idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';