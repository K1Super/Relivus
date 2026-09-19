package com.relivus.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 日志脱敏工具测试。
 */
class LogMaskUtilTest {

    @Test
    void maskFullCoversNullAndBlank() {
        assertThat(LogMaskUtil.maskFull(null)).isNull();
        assertThat(LogMaskUtil.maskFull("")).isEqualTo("<empty>");
        assertThat(LogMaskUtil.maskFull("  ")).isEqualTo("<empty>");
        assertThat(LogMaskUtil.maskFull("secret-value")).isEqualTo("******");
    }

    @Test
    void maskPartialFormats() {
        assertThat(LogMaskUtil.maskPhone("13812345678")).isEqualTo("138****5678");
        assertThat(LogMaskUtil.maskIdCard("110101199003071234")).isEqualTo("110101********1234");
        // 19 位银行卡：前 4 后 4 保留，中间 11 位全掩码
        assertThat(LogMaskUtil.maskBankCard("6222021234567890123")).isEqualTo("6222***********0123");
    }

    @Test
    void maskPartialLeavesShortAndNullValues() {
        assertThat(LogMaskUtil.maskPhone(null)).isNull();
        assertThat(LogMaskUtil.maskPhone("123")).isEqualTo("123");
        assertThat(LogMaskUtil.maskIdCard("12345678")).isEqualTo("12345678");
    }

    @Test
    void maskEmailKeepsDomainAndFirstChar() {
        assertThat(LogMaskUtil.maskEmail("tom@example.com")).isEqualTo("t***@example.com");
        assertThat(LogMaskUtil.maskEmail("no-email")).isEqualTo("no-email");
        assertThat(LogMaskUtil.maskEmail("@example.com")).isEqualTo("@example.com");
        assertThat(LogMaskUtil.maskEmail(null)).isNull();
    }

    @Test
    void maskSensitiveValueByKey() {
        assertThat(LogMaskUtil.maskSensitiveValue("password", "abc")).isEqualTo("******");
        assertThat(LogMaskUtil.maskSensitiveValue("api_key", "k")).isEqualTo("******");
        assertThat(LogMaskUtil.maskSensitiveValue("access_token", "t")).isEqualTo("******");
        assertThat(LogMaskUtil.maskSensitiveValue("Authorization", "Bearer x")).isEqualTo("******");
        // 键缺失时按保守策略全掩码
        assertThat(LogMaskUtil.maskSensitiveValue(null, "x")).isEqualTo("******");
        // 非敏感键：含 @ 视为邮箱脱敏
        assertThat(LogMaskUtil.maskSensitiveValue("email", "tom@example.com")).isEqualTo("t***@example.com");
        assertThat(LogMaskUtil.maskSensitiveValue("username", "tom")).isEqualTo("tom");
    }
}