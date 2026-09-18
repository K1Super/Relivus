package com.relivus.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建/更新 AI 配置请求。
 *
 * @param name    配置名称（唯一）
 * @param baseUrl OpenAI 兼容接口基址
 * @param apiKey  明文 API Key（仅入参，存储为 AES-GCM 密文，出参永不回传；更新时留空表示不修改）
 * @param model   模型名
 */
@Schema(description = "AI 配置请求")
public record CreateAiConfigRequest(
        @NotBlank(message = "ai config name must not be blank")
        @Size(max = 64, message = "ai config name too long")
        @Schema(description = "配置名称，唯一") String name,

        @NotBlank(message = "base url must not be blank")
        @Size(max = 255, message = "base url too long")
        @Schema(description = "OpenAI 兼容接口基址") String baseUrl,

        @Size(max = 256, message = "api key too long")
        @Schema(description = "API Key，仅入参；更新留空表示不修改") String apiKey,

        @NotBlank(message = "model must not be blank")
        @Size(max = 64, message = "model name too long")
        @Schema(description = "模型名") String model) {

    /** 是否携带新密钥（创建必填，更新可选）。 */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}