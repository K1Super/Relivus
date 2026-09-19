package com.relivus.masking;

import com.relivus.dto.MaskingConfig;

/**
 * 固定值替换。
 * 参数：value（必填）。
 */
public class FixedMask implements MaskingAlgorithm {

    @Override
    public String name() {
        return "fixed";
    }

    @Override
    public String mask(String original, MaskingConfig config) {
        String value = config.params() == null ? null : config.params().get("value");
        if (value == null) {
            throw new IllegalArgumentException("fixed: param 'value' is required");
        }
        return value;
    }
}