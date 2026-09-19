package com.relivus.masking;

import com.relivus.dto.MaskingConfig;

/**
 * 身份证号脱敏：保留前 6 后 4，中间 8 个 {@code *}，如 {@code 110101********1234}。
 * 长度不足 10 时原样返回。
 */
public class IdCardMask implements MaskingAlgorithm {

    private static final char[] MASK_SUBSTRING = {'*', '*', '*', '*', '*', '*', '*', '*'};

    @Override
    public String name() {
        return "id_card";
    }

    @Override
    public String mask(String original, MaskingConfig config) {
        if (original == null || original.length() < 10) {
            return original;
        }
        return original.substring(0, 6) + new String(MASK_SUBSTRING) + original.substring(original.length() - 4);
    }
}