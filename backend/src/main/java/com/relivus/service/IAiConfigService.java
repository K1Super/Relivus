package com.relivus.service;

import com.relivus.ai.AiProviderConfig;
import com.relivus.dto.AiConfigResponse;
import com.relivus.dto.AiTestResponse;
import com.relivus.dto.CreateAiConfigRequest;
import com.relivus.entity.AiConfigEntity;

import java.util.List;

/**
 * AI 模型配置业务接口。
 *
 * <p>负责 OpenAI 兼容上游配置的增删改查、单活跃配置切换（事务保证）、
 * 连通性测试，以及为 AI 生成器解析当前激活配置。apiKey 以 AES-GCM 密文存储，
 * 任何出参均不得携带明文 apiKey。
 */
public interface IAiConfigService {

    /**
     * 查询全部 AI 配置（按创建时间倒序）。
     *
     * @return AI 配置列表，apiKey 不回传
     */
    List<AiConfigResponse> list();

    /**
     * 新建 AI 配置；apiKey 必填，新建配置默认不激活，需显式调用 {@link #activate(long)}。
     *
     * @param request 配置创建请求
     * @return 新建配置的出参
     */
    AiConfigResponse create(CreateAiConfigRequest request);

    /**
     * 更新 AI 配置；请求携带非空白 apiKey 时重新加密，留空时保留原密文。
     *
     * @param id      配置 ID
     * @param request 配置更新请求
     * @return 更新后的配置出参
     */
    AiConfigResponse update(long id, CreateAiConfigRequest request);

    /**
     * 删除 AI 配置；不自动切换激活项，删除激活配置后生成侧实时解析将找不到激活配置。
     *
     * @param id 配置 ID
     */
    void delete(long id);

    /**
     * 将指定配置置为唯一激活配置（事务内先全部置非活跃再激活目标）。
     *
     * @param id 配置 ID
     * @return 激活后的配置出参
     */
    AiConfigResponse activate(long id);

    /**
     * 对指定配置做一次连通性测试（拉取模型列表）；上游不可达时抛出携带错误码的业务异常。
     *
     * @param id 配置 ID
     * @return 连通性测试结果
     */
    AiTestResponse test(long id);

    /**
     * 解析当前激活配置对应的上游连接参数；无激活配置时抛出 AI_CONFIG_NOT_FOUND 业务异常，
     * 由调用方决定提示或降级。
     *
     * @return 激活配置的上游参数
     */
    AiProviderConfig resolveActive();

    /**
     * 按 ID 获取配置实体，不存在时抛出 AI_CONFIG_NOT_FOUND 业务异常。
     *
     * @param id 配置 ID
     * @return 配置实体（含密文 apiKey，仅限服务端内部使用，禁止直接出参）
     */
    AiConfigEntity requireEntity(long id);
}
