package com.relivus.schema;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.relivus.schema.model.TableMetadata;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Schema 缓存。
 *
 * <p>Caffeine 最大 100 项，写入后 10 分钟过期。key 为连接 ID，value 为该连接全量表元数据。
 */
@Component
public class SchemaCache {

    private static final Duration EXPIRE_AFTER_WRITE = Duration.ofMinutes(10);

    private final Cache<Long, List<TableMetadata>> cache;

    public SchemaCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(EXPIRE_AFTER_WRITE)
                .build();
    }

    /**
     * 按连接 ID 取缓存；未命中则由 supplier 加载并写入。
     */
    public List<TableMetadata> get(Long connectionId, Supplier<List<TableMetadata>> supplier) {
        return cache.get(connectionId, ignored -> supplier.get());
    }

    /** 手动失效（连接信息变更时调用）。 */
    public void evict(Long connectionId) {
        cache.invalidate(connectionId);
    }

    /** 清空全部缓存。 */
    public void clear() {
        cache.invalidateAll();
    }
}