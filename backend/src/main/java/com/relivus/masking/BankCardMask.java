package com.relivus.masking;

import com.relivus.dto.MaskingConfig;

/**
 * 银行卡号脱敏：保留前 4 后 4，中间 8 个 {@code *}，如 {@code 6222********1234}。
 * 长度不足 8 时原样返回。
 */
public class BankCardMask implements MaskingAlgorithm {

    private static final char[] MASK = {'*', '*', '*', '*', '*', '*', '*', '*'};

    @Override
    public String name() {
        return "bank_card";
    }

    @Override
    public String mask(String original, MaskingConfig config) {
        if (original == null || original.length() < 8) {
            return original;
        }
        return original.substring(0, 4) + new String(MASK) + original.substring(original.length() - 4);
    }
}