-- ============================================================
-- Relivus 元库初始化（MySQL 方言）V2：AI 模型配置
-- 说明：api_key 以 AES-GCM 密文存储（Base64(IV + cipherText + tag)），绝无明文；
--       active 单活跃由应用层事务保证（激活前将其他配置置为 inactive）。
-- ============================================================

CREATE TABLE df_ai_config (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(64)  NOT NULL,
    base_url       VARCHAR(255) NOT NULL COMMENT 'OpenAI 兼容接口基址，如 https://api.deepseek.com/v1',
    api_key_cipher VARCHAR(512) NOT NULL COMMENT 'AES-GCM 密文，响应不回传明文',
    model          VARCHAR(64)  NOT NULL COMMENT '模型名，如 deepseek-chat / glm-4-plus',
    active         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '当前激活的 AI 配置（生成任务使用）',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_config_name (name),
    KEY idx_ai_config_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 模型配置';