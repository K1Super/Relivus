package com.relivus.entity;

import java.time.LocalDateTime;

/**
 * AI 模型配置实体（df_ai_config）。
 *
 * <p>apiKey 以 AES-GCM 密文保存于 {@code apiKeyCipher}，绝不以明文驻留实体或日志。
 */
public class AiConfigEntity {

    private Long id;
    private String name;
    /** OpenAI 兼容接口基址。 */
    private String baseUrl;
    /** AES-GCM 密文（Base64(IV + cipherText + tag)）。 */
    private String apiKeyCipher;
    private String model;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKeyCipher() { return apiKeyCipher; }
    public void setApiKeyCipher(String apiKeyCipher) { this.apiKeyCipher = apiKeyCipher; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}