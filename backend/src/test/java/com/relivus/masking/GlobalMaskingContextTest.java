package com.relivus.masking;

import com.relivus.common.exception.RelivusException;
import com.relivus.config.RelivusProperties;
import com.relivus.dto.MaskingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 全局脱敏上下文测试（缓存一致性、持久化回退）。
 *
 * <p>通过反射注入 mock 映射仓库，验证三级读取与写入回退逻辑。
 */
class GlobalMaskingContextTest {

    private GlobalMaskingContext context;
    private MaskMappingRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) 7;
        }
        RelivusProperties props = new RelivusProperties();
        props.getMasking().setHmacKey(Base64.getEncoder().encodeToString(key));

        context = new GlobalMaskingContext(mock(DataSource.class), props);
        repository = mock(MaskMappingRepository.class);
        Field repoField = GlobalMaskingContext.class.getDeclaredField("repository");
        repoField.setAccessible(true);
        repoField.set(context, repository);
    }

    @Test
    void nullOriginalPassesThrough() {
        assertThat(context.mask("g", null, MaskingConfig.of("phone", "g"))).isNull();
    }

    @Test
    void unknownAlgorithmThrows4001() {
        assertThatThrownBy(() -> context.mask("g", "13812345678",
                MaskingConfig.of("no_such", "g")))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", com.relivus.common.exception.ErrorCode.MASKING_FAILED.getCode());
    }

    @Test
    void cachesResultAndWritesMappingOnce() {
        when(repository.find(anyString(), anyString())).thenReturn(null);
        when(repository.saveIfAbsent(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("m123");

        MaskingConfig config = MaskingConfig.of("phone", "groupA");
        String first = context.mask("groupA", "13812345678", config);
        String second = context.mask("groupA", "13812345678", config);

        assertThat(first).isEqualTo("m123");
        assertThat(second).isEqualTo("m123");
        // 缓存命中：第二次不再触达 repository
        verify(repository, times(1)).find(anyString(), anyString());
        verify(repository, times(1)).saveIfAbsent(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void fallsBackToPersistedValue() {
        when(repository.find(eq("g"), anyString())).thenReturn("existing_mask");
        String value = context.mask("g", "13900001111", MaskingConfig.of("phone", "g"));
        assertThat(value).isEqualTo("existing_mask");
        verify(repository, never()).saveIfAbsent(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void returnsComputedValueWhenPersistReturnsNull() {
        when(repository.find(anyString(), anyString())).thenReturn(null);
        when(repository.saveIfAbsent(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(null);
        String value = context.mask("g", "13812345678", MaskingConfig.of("phone", "g"));
        assertThat(value).isEqualTo("138****5678");
    }

    @Test
    void resolvesAlgorithmsByRegisteredName() {
        assertThat(context.resolveAlgorithm("phone")).isInstanceOf(PhoneMask.class);
        assertThat(context.resolveAlgorithm("hmac")).isInstanceOf(HmacHash.class);
        assertThat(context.resolveAlgorithm("unknown")).isNull();
        assertThat(context.resolveAlgorithm(null)).isNull();
        assertThat(context.algorithmNames())
                .containsExactlyInAnyOrder("fixed", "regex", "hmac", "phone", "id_card", "bank_card", "faker");
    }

    @Test
    void invalidHmacKeyBase64FailsFast() {
        RelivusProperties props = new RelivusProperties();
        props.getMasking().setHmacKey("not-valid-base64!!!");
        assertThatThrownBy(() -> new GlobalMaskingContext(mock(DataSource.class), props))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", com.relivus.common.exception.ErrorCode.INTERNAL_ERROR.getCode());
    }
}