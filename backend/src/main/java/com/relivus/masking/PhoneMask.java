package com.relivus.masking;

import com.relivus.dto.MaskingConfig;

/**
 * 手机号脱敏（DOC-04）：保留前 3 后 4，中间 {@code ****}，如 {@code 138****1234}。
 * 长度不足 7 时原样返回，不破坏短字段。
 */
public class PhoneMask implements MaskingAlgorithm {

    private static final char[] MASK = {'*', '*', '*', '*'};

    @Override
    public String name() {
        return "phone";
    }

    @Override
    public String mask(String original, MaskingConfig config) {
        if (original == null || original.length() < 7) {
            return original;
        }
        return original.substring(0, 3) + new String(MASK) + original.substring(original.length() - 4);
    }
}