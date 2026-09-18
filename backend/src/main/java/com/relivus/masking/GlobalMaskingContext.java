package com.relivus.masking;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.config.DataSourceConfig;
import com.relivus.config.RelivusProperties;
import com.relivus.dto.MaskingConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * 全局脱敏上下文（DOC-04 / DOC-11.3）。
 *
 * <p>同一原始值在同 {@link MaskingConfig#columnGroup()} 内跨表、跨任务保持一致：
 * 读取顺序 = 内存缓存（Caffeine，1 万条 LRU）→ 元库映射表 → 实时计算并持久化。
 * 线程安全：Caffeine 并发 + 映射表「INSERT 忽略 + 回读」兜底并发写。
 */
@Component
public class GlobalMaskingContext {

    private static final int CACHE_MAX_SIZE = 10_000;

    private final Cache<String, String> cache = Caffeine.newBuilder().maximumSize(CACHE_MAX_SIZE).build();
    private final MaskMappingRepository repository;
    private final Map<String, MaskingAlgorithm> algorithms;

    public GlobalMaskingContext(@Qualifier(DataSourceConfig.META_DATASOURCE) DataSource metaDataSource,
                                RelivusProperties properties) {
        this.repository = new MaskMappingRepository(metaDataSource, detectMetaDialect(properties));
        this.algorithms = Map.of(
                "fixed", new FixedMask(),
                "regex", new RegexMask(),
                "hmac", new HmacHash(decodeHmacKey(properties)),
                "phone", new PhoneMask(),
                "id_card", new IdCardMask(),
                "bank_card", new BankCardMask(),
                "faker", new FakerReplace());
    }

    /**
     * 对原始值执行脱敏并保证同组一致性。
     *
     * @param columnGroup 分组；同组内同一原始值共享一个掩码结果
     * @param original    原始值；null 原样返回
     * @param config      算法配置（algorithm 必须存在）
     */
    public String mask(String columnGroup, String original, MaskingConfig config) {
        if (original == null) {
            return null;
        }
        MaskingAlgorithm algorithm = algorithms.get(config.algorithm());
        if (algorithm == null) {
            throw new RelivusException(ErrorCode.MASKING_FAILED,
                    "未知脱敏算法 '" + config.algorithm() + "'（支持：" + String.join("/", algorithms.keySet()) + "）");
        }
        String hash = sha256Hex(original);
        String cacheKey = columnGroup + ":" + hash;
        String cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        String persisted = repository.find(columnGroup, hash);
        if (persisted != null) {
            cache.put(cacheKey, persisted);
            return persisted;
        }
        String masked = algorithm.mask(original, config);
        String saved = repository.saveIfAbsent(columnGroup, hash, masked, config.algorithm(), config.keyVersion());
        // 同组一致性：以持久化值为准（并发冲突时数据库已存在映射），缓存最终生效值，
        // 避免首次返回 DB 值、二次命中缓存却返回计算值的不一致。
        String effective = saved != null ? saved : masked;
        cache.put(cacheKey, effective);
        return effective;
    }

    /**
     * 按注册名解析算法实现（不存在返回 null）。
     *
     * <p>供预览等不落库场景直接调用算法，与 {@link #mask} 保持同一注册表与校验口径。
     */
    public MaskingAlgorithm resolveAlgorithm(String name) {
        return name == null ? null : algorithms.get(name);
    }

    /** 已注册算法名集合（错误提示与测试断言用）。 */
    public java.util.Set<String> algorithmNames() {
        return algorithms.keySet();
    }

    private static String detectMetaDialect(RelivusProperties properties) {
        String url = properties.getMeta().getUrl();
        return (url != null && url.contains("postgresql")) ? "postgresql" : "mysql";
    }

    private static byte[] decodeHmacKey(RelivusProperties properties) {
        try {
            return Base64.getDecoder().decode(properties.getMasking().getHmacKey());
        } catch (IllegalArgumentException e) {
            throw new RelivusException(ErrorCode.INTERNAL_ERROR,
                    "RELIVUS_HMAC_KEY 非法，须为 Base64 编码的 32 字节密钥", e);
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}