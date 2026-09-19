package com.relivus.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志脱敏工具（安全红线）。
 *
 * <p>任何日志输出前必须经本工具处理：密码/Token/密钥完全掩码；手机号、身份证、银行卡、
 * 邮箱等个人隐私按"部分保留"策略脱敏。业务代码不得绕过。
 */
public final class LogMaskUtil {

    private LogMaskUtil() {
    }

    /** 完全掩码。 */
    public static final String FULL_MASK = "******";
    private static final String EMPTY = "<empty>";

    /** 手机号：前 3 后 4。 */
    static final String PHONE_REGEX = "(?<=^\\d{3})\\d{4}(?=\\d{4}$)";
    /** 身份证：前 6 后 4。 */
    static final String ID_CARD_REGEX = "(?<=^\\d{6})\\d{7,8}(?=\\d{4}$)";
    /** 银行卡/账号：前 4 后 4。 */
    static final String BANK_CARD_REGEX = "(?<=^\\d{4})\\d+(?=\\d{4}$)";

    /** 敏感字段名，命中即完全掩码。 */
    static final String[] SENSITIVE_KEYS = {
            "password", "pwd", "token", "access_token", "refresh_token",
            "authorization", "secret", "api_key", "apikey", "key", "aes_key",
            "hmac_key", "credential", "cookie", "session", "password_cipher"
    };

    /** 敏感值完全掩码（密码、Token、密钥等）。 */
    public static String maskFull(String value) {
        if (value == null) {
            return null;
        }
        return value.isBlank() ? EMPTY : FULL_MASK;
    }

    /** 手机号脱敏：138****1234。 */
    public static String maskPhone(String value) {
        return maskPartial(value, PHONE_REGEX);
    }

    /** 身份证脱敏：110101********1234。 */
    public static String maskIdCard(String value) {
        return maskPartial(value, ID_CARD_REGEX);
    }

    /** 银行卡脱敏：6222********1234。 */
    public static String maskBankCard(String value) {
        return maskPartial(value, BANK_CARD_REGEX);
    }

    /** 邮箱脱敏：t***@example.com。 */
    public static String maskEmail(String value) {
        if (value == null || !value.contains("@")) {
            return value;
        }
        int at = value.indexOf('@');
        String local = value.substring(0, at);
        String domain = value.substring(at);
        if (local.isEmpty()) {
            return value;
        }
        return local.charAt(0) + "***" + domain;
    }

    /** 按键名脱敏 JSON 片段中的敏感值（简版：仅用与现有 JSON 结构）。 */
    public static String maskSensitiveValue(String key, String value) {
        if (value == null) {
            return null;
        }
        if (key == null) {
            // 键不可用时按保守策略全掩码，避免敏感值泄漏（安全合规优先）
            return maskFull(value);
        }
        String lowerKey = key.toLowerCase();
        for (String sensitive : SENSITIVE_KEYS) {
            if (lowerKey.contains(sensitive)) {
                return maskFull(value);
            }
        }
        return value.contains("@") ? maskEmail(value) : value;
    }

    /** 部分保留脱敏：掩码位数与命中文本长度一致（手机号 4 位、18 位身份证 8 位等）。 */
    private static String maskPartial(String value, String regex) {
        if (value == null || value.length() < 8) {
            return value;
        }
        Matcher matcher = Pattern.compile(regex).matcher(value);
        StringBuffer sb = new StringBuffer(value.length());
        while (matcher.find()) {
            matcher.appendReplacement(sb,
                    Matcher.quoteReplacement("*".repeat(Math.max(4, matcher.group().length()))));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}