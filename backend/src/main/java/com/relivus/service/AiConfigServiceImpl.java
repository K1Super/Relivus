package com.relivus.service;

import com.relivus.ai.AiHttpClient;
import com.relivus.ai.AiProviderConfig;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dto.AiConfigResponse;
import com.relivus.dto.AiTestResponse;
import com.relivus.dto.CreateAiConfigRequest;
import com.relivus.entity.AiConfigEntity;
import com.relivus.repository.AiConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 模型配置服务。
 *
 * <p>职责：配置 CRUD、激活切换（单活跃由事务保证）、连通性测试、解密并装配明文配置。
 * apiKey 解密后仅在 {@link AiProviderConfig} 内存对象中短暂存在，绝不出现在日志与响应中。
 */
@Service
public class AiConfigServiceImpl implements IAiConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(AiConfigServiceImpl.class);

    private final AiConfigRepository repository;
    private final CryptoService cryptoService;
    private final AiHttpClient aiHttpClient;

    public AiConfigServiceImpl(AiConfigRepository repository, CryptoService cryptoService,
                           AiHttpClient aiHttpClient) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.aiHttpClient = aiHttpClient;
    }

    /** 全部配置列表。 */
    @Override
    public List<AiConfigResponse> list() {
        return repository.findAll().stream().map(AiConfigResponse::from).toList();
    }

    /** 创建配置：apiKey 必填（非空白），重名抛资源冲突，密钥加密后落库。 */
    @Override
    @Transactional
    public AiConfigResponse create(CreateAiConfigRequest request) {
        if (!request.hasApiKey()) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED, "apiKey 不能为空");
        }
        if (repository.existsByNameExcludingId(request.name(), null)) {
            throw new RelivusException(ErrorCode.RESOURCE_CONFLICT, "AI 配置名已存在：" + request.name());
        }
        AiConfigEntity entity = new AiConfigEntity();
        entity.setName(request.name());
        entity.setBaseUrl(request.baseUrl());
        entity.setApiKeyCipher(cryptoService.encrypt(request.apiKey()));
        entity.setModel(request.model());
        entity.setActive(false);
        Long id = repository.insert(entity);
        return AiConfigResponse.from(requireEntity(id));
    }

    /** 更新配置：apiKey 留空则保留旧密文，否则重新加密；updated_at 由 DB 维护。 */
    @Override
    @Transactional
    public AiConfigResponse update(long id, CreateAiConfigRequest request) {
        AiConfigEntity entity = requireEntity(id);
        if (!entity.getName().equals(request.name())
                && repository.existsByNameExcludingId(request.name(), id)) {
            throw new RelivusException(ErrorCode.RESOURCE_CONFLICT, "AI 配置名已存在：" + request.name());
        }
        entity.setName(request.name());
        entity.setBaseUrl(request.baseUrl());
        entity.setModel(request.model());
        if (request.hasApiKey()) {
            entity.setApiKeyCipher(cryptoService.encrypt(request.apiKey()));
        }
        repository.update(entity);
        return AiConfigResponse.from(requireEntity(id));
    }

    /** 删除配置；若删除的是当前激活配置不做额外处理（生成时实时解析激活项）。 */
    @Override
    @Transactional
    public void delete(long id) {
        requireEntity(id);
        repository.deleteById(id);
    }

    /** 激活配置：不存在抛 160001；先全部置非活跃再激活目标，保证单活跃。 */
    @Override
    @Transactional
    public AiConfigResponse activate(long id) {
        requireEntity(id);
        repository.deactivateAll();
        repository.activateById(id);
        return AiConfigResponse.from(requireEntity(id));
    }

    /** 连通性测试：ping 失败时 RelivusException（160002）原样上抛供前端展示。 */
    @Override
    public AiTestResponse test(long id) {
        AiConfigEntity entity = requireEntity(id);
        AiProviderConfig cfg = toProviderConfig(entity);
        String detail = aiHttpClient.ping(cfg);
        return new AiTestResponse(true, detail);
    }

    /** 解析当前激活配置为内存明文对象；无激活配置抛 160001。 */
    @Override
    public AiProviderConfig resolveActive() {
        AiConfigEntity entity = repository.findActive()
                .orElseThrow(() -> new RelivusException(ErrorCode.AI_CONFIG_NOT_FOUND,
                        "未配置可用的 AI 模型，请到设置页添加并激活"));
        return toProviderConfig(entity);
    }

    @Override
    public AiConfigEntity requireEntity(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RelivusException(ErrorCode.AI_CONFIG_NOT_FOUND,
                        "AI 配置不存在：" + id));
    }

    private AiProviderConfig toProviderConfig(AiConfigEntity entity) {
        String apiKey = cryptoService.decrypt(entity.getApiKeyCipher());
        LOG.debug("AI 配置加载：name={}, model={}", entity.getName(), entity.getModel());
        return new AiProviderConfig(entity.getName(), entity.getBaseUrl(), apiKey, entity.getModel());
    }
}