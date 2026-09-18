package com.relivus.service;

import com.relivus.ai.AiHttpClient;
import com.relivus.ai.AiProviderConfig;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dto.AiTestResponse;
import com.relivus.dto.CreateAiConfigRequest;
import com.relivus.entity.AiConfigEntity;
import com.relivus.repository.AiConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 配置服务测试：加密落库 / 重名冲突 / 密钥空白 / 激活事务顺序 / 激活解析 / 连通性测试。
 */
class AiConfigServiceTest {

    private AiConfigRepository repository;
    private CryptoService cryptoService;
    private AiHttpClient aiHttpClient;
    private AiConfigService service;

    @BeforeEach
    void setUp() {
        repository = mock(AiConfigRepository.class);
        cryptoService = mock(CryptoService.class);
        aiHttpClient = mock(AiHttpClient.class);
        service = new AiConfigService(repository, cryptoService, aiHttpClient);
    }

    private static AiConfigEntity entity(long id, String name, String cipher) {
        AiConfigEntity e = new AiConfigEntity();
        e.setId(id);
        e.setName(name);
        e.setBaseUrl("http://x");
        e.setApiKeyCipher(cipher);
        e.setModel("m");
        e.setActive(false);
        return e;
    }

    private static CreateAiConfigRequest request(String name, String apiKey) {
        return new CreateAiConfigRequest(name, "http://x", apiKey, "m");
    }

    @Test
    void createEncryptsAndPersists() {
        when(repository.existsByNameExcludingId("n", null)).thenReturn(false);
        when(cryptoService.encrypt("key")).thenReturn("CIPHER");
        when(repository.insert(any(AiConfigEntity.class))).thenReturn(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(entity(5L, "n", "CIPHER")));

        var response = service.create(request("n", "key"));

        verify(cryptoService).encrypt("key");
        ArgumentCaptor<AiConfigEntity> captor = ArgumentCaptor.forClass(AiConfigEntity.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getApiKeyCipher()).isEqualTo("CIPHER");
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(response.id()).isEqualTo(5L);
    }

    @Test
    void createDuplicateNameThrowsConflict() {
        when(repository.existsByNameExcludingId("n", null)).thenReturn(true);
        assertThatThrownBy(() -> service.create(request("n", "key")))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.RESOURCE_CONFLICT.getCode());
    }

    @Test
    void createBlankApiKeyThrowsValidationFailed() {
        when(repository.existsByNameExcludingId("n", null)).thenReturn(false);
        assertThatThrownBy(() -> service.create(request("n", "  ")))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED.getCode());
    }

    @Test
    void updateBlankApiKeyKeepsOldCipher() {
        AiConfigEntity existing = entity(1L, "n", "OLD_CIPHER");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByNameExcludingId("n", 1L)).thenReturn(false);

        service.update(1L, request("n", ""));

        ArgumentCaptor<AiConfigEntity> captor = ArgumentCaptor.forClass(AiConfigEntity.class);
        verify(repository).update(captor.capture());
        assertThat(captor.getValue().getApiKeyCipher()).isEqualTo("OLD_CIPHER");
        verify(cryptoService, never()).encrypt(any());
    }

    @Test
    void activateDeactivatesAllThenActivatesTarget() {
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L, "n", "CIPHER")));

        service.activate(1L);

        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).deactivateAll();
        inOrder.verify(repository).activateById(1L);
    }

    @Test
    void resolveActiveMissingThrowsConfigNotFound() {
        when(repository.findActive()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolveActive())
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.AI_CONFIG_NOT_FOUND.getCode());
    }

    @Test
    void resolveActiveReturnsDecryptedConfig() {
        when(repository.findActive()).thenReturn(Optional.of(entity(1L, "n", "CIPHER")));
        when(cryptoService.decrypt("CIPHER")).thenReturn("plain");

        AiProviderConfig config = service.resolveActive();
        assertThat(config.apiKey()).isEqualTo("plain");
        assertThat(config.model()).isEqualTo("m");
        assertThat(config.name()).isEqualTo("n");
    }

    @Test
    void testReturnsReachableTrue() {
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L, "n", "CIPHER")));
        when(cryptoService.decrypt("CIPHER")).thenReturn("plain");
        when(aiHttpClient.ping(any(AiProviderConfig.class))).thenReturn("hello");

        AiTestResponse result = service.test(1L);
        assertThat(result.reachable()).isTrue();
        assertThat(result.detail()).isEqualTo("hello");
    }

    @Test
    void testUpstreamFailurePropagates() {
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L, "n", "CIPHER")));
        when(cryptoService.decrypt("CIPHER")).thenReturn("plain");
        when(aiHttpClient.ping(any(AiProviderConfig.class)))
                .thenThrow(new RelivusException(ErrorCode.AI_UPSTREAM_FAILED, "up"));

        assertThatThrownBy(() -> service.test(1L))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.AI_UPSTREAM_FAILED.getCode());
        verify(repository, never()).update(any(AiConfigEntity.class));
    }
}