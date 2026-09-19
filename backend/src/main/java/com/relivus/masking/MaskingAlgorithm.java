package com.relivus.masking;

import com.relivus.dto.MaskingConfig;

/**
 * 脱敏算法 SPI。
 *
 * <p>实现必须线程安全且无状态（密钥由 {@link HmacHash} 构造注入，参数一律走 {@link MaskingConfig#params()}）。
 */
public interface MaskingAlgorithm {

    /** 算法注册名（配置与日志使用）。 */
    String name();

    /** 对单个原始值执行脱敏。 */
    String mask(String original, MaskingConfig config);
}