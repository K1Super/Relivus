package com.relivus.ai;

import com.relivus.common.exception.RelivusException;

import java.util.List;

/**
 * OpenAI 兼容上游 HTTP 客户端 SPI。
 *
 * <p>实现负责网络调用、超时、有限重试与响应解析。所有业务性失败必须抛
 * {@link RelivusException} 并携带对应 {@code ErrorCode}，禁止裸异常向上传播。
 */
public interface AiHttpClient {

    /**
     * 请求上游一次性生成 {@code count} 个值，返回顺序列表。
     *
     * <p>内部负责有限重试（5xx / IO / 超时，次数由配置控制）与超时控制。语义化失败
     * （上游返回非 2xx / 连接失败）抛 {@code AI_UPSTREAM_FAILED}，响应无法解析等系统性
     * 失败抛 {@code AI_SERVICE_ERROR}。
     *
     * @param cfg   明文供应商配置（含解密后的 apiKey，仅内存）
     * @param prompt 用户生成提示词
     * @param count 期望返回值的数量
     * @return 顺序值列表（数量与 {@code count} 一致，已补齐/截断）
     */
    List<String> completeBatch(AiProviderConfig cfg, String prompt, int count);

    /**
     * 连通性测试：发送一个最小请求，返回模型回复的前 50 字符摘要。
     *
     * <p>失败抛 {@code AI_UPSTREAM_FAILED}（由调用方透传给前端 160002）。
     *
     * @param cfg 明文供应商配置
     * @return 回复摘要（最多 50 字符）
     */
    String ping(AiProviderConfig cfg);
}