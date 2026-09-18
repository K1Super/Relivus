package com.relivus.ai;

/**
 * AI 供应商明文配置（仅内存传递，绝不出现在日志、响应或任何持久化密文之外）。
 *
 * <p>该对象由 {@code AiConfigService.resolveActive()} 解密构造，生命周期极短：
 * 仅用于本次 HTTP 调用。禁止打印 {@code apiKey} 或 {@code baseUrl} 拼出的完整明文，
 * 日志中只允许出现 {@code name}/{@code model}。
 *
 * @param name    配置名称（日志用）
 * @param baseUrl OpenAI 兼容接口基址，如 {@code https://api.deepseek.com/v1}
 * @param apiKey  解密后的明文密钥（敏感，勿外泄）
 * @param model   模型名
 */
public record AiProviderConfig(String name, String baseUrl, String apiKey, String model) {
}