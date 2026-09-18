package com.relivus.masking;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dto.MaskingConfig;

import java.util.regex.Pattern;

/**
 * 正则替换（DOC-04）：按 pattern 匹配，replacement 替换，支持 $1 分组引用保留。
 * 参数：pattern（必填，最长 200）、replacement（必填）。
 * 参数缺失/超长/非法时抛 {@link ErrorCode#VALIDATION_FAILED}，避免落入服务端兜底错误码。
 */
public class RegexMask implements MaskingAlgorithm {

    private static final int MAX_PATTERN_LENGTH = 200;

    @Override
    public String name() {
        return "regex";
    }

    @Override
    public String mask(String original, MaskingConfig config) {
        String pattern = config.params() == null ? null : config.params().get("pattern");
        String replacement = config.params() == null ? null : config.params().get("replacement");
        if (pattern == null || replacement == null) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                    "regex: params 'pattern' and 'replacement' are required");
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                    "regex: pattern 长度不能超过 " + MAX_PATTERN_LENGTH);
        }
        try {
            return Pattern.compile(pattern).matcher(original).replaceAll(replacement);
        } catch (RuntimeException e) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                    "regex: invalid pattern '" + pattern + "'", e);
        }
    }
}