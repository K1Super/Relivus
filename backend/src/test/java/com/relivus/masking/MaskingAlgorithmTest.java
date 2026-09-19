package com.relivus.masking;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dto.MaskingConfig;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 脱敏算法单元测试（确定性、格式保留）。
 */
class MaskingAlgorithmTest {

    private static MaskingConfig cfg(String algorithm, Map<String, String> params) {
        return new MaskingConfig(algorithm, params, "g", 1);
    }

    @Test
    void phoneMaskKeepsHeadTailAndMasksMiddle() {
        PhoneMask mask = new PhoneMask();
        assertThat(mask.name()).isEqualTo("phone");
        assertThat(mask.mask("13812345678", cfg("phone", Map.of()))).isEqualTo("138****5678");
        // 长度不足 7 原样返回
        assertThat(mask.mask("123456", cfg("phone", Map.of()))).isEqualTo("123456");
        assertThat(mask.mask(null, cfg("phone", Map.of()))).isNull();
    }

    @Test
    void idCardMaskKeepsFirst6Last4() {
        IdCardMask mask = new IdCardMask();
        assertThat(mask.mask("110101199003071234", cfg("id_card", Map.of())))
                .isEqualTo("110101********1234");
        assertThat(mask.mask("12345", cfg("id_card", Map.of()))).isEqualTo("12345");
        assertThat(mask.mask(null, cfg("id_card", Map.of()))).isNull();
    }

    @Test
    void bankCardMaskKeepsFirst4Last4() {
        BankCardMask mask = new BankCardMask();
        assertThat(mask.mask("6222021234567890123", cfg("bank_card", Map.of())))
                .isEqualTo("6222********0123");
        assertThat(mask.mask("1234567", cfg("bank_card", Map.of()))).isEqualTo("1234567");
        assertThat(mask.mask(null, cfg("bank_card", Map.of()))).isNull();
    }

    @Test
    void fixedMaskReplacesWithConfiguredValue() {
        FixedMask mask = new FixedMask();
        assertThat(mask.mask("any", cfg("fixed", Map.of("value", "******")))).isEqualTo("******");
        assertThatThrownBy(() -> mask.mask("any", cfg("fixed", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void regexMaskSupportsGroupReplacement() {
        RegexMask mask = new RegexMask();
        assertThat(mask.mask("abc123", cfg("regex", Map.of("pattern", "\\d+", "replacement", "***"))))
                .isEqualTo("abc***");
        assertThat(mask.mask("2026-09", cfg("regex", Map.of("pattern", "(\\d{4})-(\\d{2})",
                "replacement", "$2/$1")))).isEqualTo("09/2026");
        assertThatThrownBy(() -> mask.mask("x", cfg("regex", Map.of("pattern", "\\d+"))))
                .isInstanceOf(RelivusException.class)
                .extracting(e -> ((RelivusException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> mask.mask("x", cfg("regex", Map.of("pattern", "(", "replacement", "y"))))
                .isInstanceOf(RelivusException.class)
                .extracting(e -> ((RelivusException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void hmacHashIsDeterministicAndVersionDependent() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        HmacHash hash = new HmacHash(key);
        assertThat(hash.name()).isEqualTo("hmac");

        String first = hash.mask("13812345678", cfg("hmac", Map.of()));
        String second = hash.mask("13812345678", cfg("hmac", Map.of()));
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");

        String v2 = hash.mask("13812345678", new MaskingConfig("hmac", Map.of(), "g", 2));
        assertThat(v2).isNotEqualTo(first);

        // 不同输入不同输出
        assertThat(hash.mask("a", cfg("hmac", Map.of()))).isNotEqualTo(hash.mask("b", cfg("hmac", Map.of())));
    }

    @Test
    void fakerReplaceDispatchesProviders() {
        FakerReplace mask = new FakerReplace();
        assertThat(mask.name()).isEqualTo("faker");
        String defaultName = mask.mask("orig", cfg("faker", Map.of()));
        assertThat(defaultName).isNotBlank();

        assertThat(mask.mask("orig", cfg("faker", Map.of("provider", "email")))).contains("@");
        assertThat(mask.mask("orig", cfg("faker", Map.of("provider", "phone")))).isNotBlank();
        assertThat(mask.mask("orig", cfg("faker", Map.of("provider", "address")))).isNotBlank();
        assertThat(mask.mask("orig", cfg("faker", Map.of("provider", "company")))).isNotBlank();
        assertThat(mask.mask("orig", cfg("faker", Map.of("provider", "city")))).isNotBlank();
        assertThat(mask.mask("orig", cfg("faker", Map.of("provider", "uuid")))).isNotBlank();
        assertThat(mask.mask("orig", cfg("faker", Map.of("provider", "text")))).isNotBlank();

        assertThatThrownBy(() -> mask.mask("orig", cfg("faker", Map.of("provider", "nope"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}