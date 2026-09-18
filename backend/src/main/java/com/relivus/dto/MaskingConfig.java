package com.relivus.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 脱敏算法配置（DOC-11.5）。
 *
 * @param algorithm   算法名：fixed / regex / hmac / phone / id_card / bank_card / faker
 * @param params      算法参数（字符串键值，如 fixed 的 value、regex 的 pattern/replacement、faker 的 provider）
 * @param columnGroup 映射组（同一组的同一原始值跨表脱敏结果一致）；缺省由引擎按列推断
 * @param keyVersion  密钥版本（hmac 算法使用，默认 1）
 */
@Schema(description = "脱敏算法配置")
public record MaskingConfig(
        String algorithm,
        Map<String, String> params,
        String columnGroup,
        int keyVersion) {

    public MaskingConfig {
        if (keyVersion <= 0) {
            keyVersion = 1;
        }
    }

    /** 便捷构造：仅算法与分组，版本 1。 */
    public static MaskingConfig of(String algorithm, String columnGroup) {
        return new MaskingConfig(algorithm, Map.of(), columnGroup, 1);
    }

    /** 便捷构造：完整参数。 */
    public static MaskingConfig of(String algorithm, Map<String, String> params, String columnGroup, int keyVersion) {
        return new MaskingConfig(algorithm, params, columnGroup, keyVersion);
    }
}