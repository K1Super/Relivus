package com.relivus.generator;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.schema.model.ColumnMetadata;
import net.datafaker.Faker;

import java.util.Locale;

/**
 * 正则生成器：按正则表达式生成随机匹配串（DOC-03，手机号等）。
 * 参数：pattern（必填，最长 200，如 {@code 1[3-9]\\d{9}}）。
 * 基于 DataFaker regexify 实现；pattern 非法时回退为「int 转串」避免整表失败。
 */
public class RegexGenerator implements ValueGenerator {

    private static final int MAX_PATTERN_LENGTH = 200;

    private final Faker faker = new Faker(Locale.US);

    @Override
    public String name() {
        return "regex";
    }

    @Override
    public Object generate(GenerationContext context) {
        String pattern = context.param("pattern", null);
        if (pattern == null || pattern.isBlank()) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED, "regex: param 'pattern' is required");
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                    "regex: pattern 长度不能超过 " + MAX_PATTERN_LENGTH);
        }
        try {
            return faker.regexify(pattern);
        } catch (RuntimeException e) {
            return String.valueOf(System.nanoTime());
        }
    }

    @Override
    public boolean supports(ColumnMetadata column) {
        return false;
    }
}