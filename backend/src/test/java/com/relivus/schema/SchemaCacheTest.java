package com.relivus.schema;

import com.relivus.schema.model.TableMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema 缓存测试（DOC-02）。
 *
 * <p>验证缓存命中只加载一次、手动失效后重新加载、清空全失效。
 */
class SchemaCacheTest {

    private final SchemaCache cache = new SchemaCache();

    @Test
    void loadsOnceThenCaches() {
        AtomicInteger calls = new AtomicInteger();
        List<TableMetadata> first = cache.get(1L, () -> {
            calls.incrementAndGet();
            return List.of(table("a"));
        });
        assertThat(first).hasSize(1);

        List<TableMetadata> second = cache.get(1L, () -> {
            calls.incrementAndGet();
            return List.of(table("b"));
        });
        assertThat(calls.get()).isEqualTo(1);
        assertThat(second.get(0).tableName()).isEqualTo("a"); // 命中缓存返回旧值
    }

    @Test
    void evictForcesReload() {
        AtomicInteger calls = new AtomicInteger();
        cache.get(1L, () -> {
            calls.incrementAndGet();
            return List.of(table("a"));
        });
        cache.evict(1L);
        cache.get(1L, () -> {
            calls.incrementAndGet();
            return List.of(table("b"));
        });
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void evictOnlyAffectsTargetConnection() {
        AtomicInteger calls = new AtomicInteger();
        cache.get(1L, () -> {
            calls.incrementAndGet();
            return List.of(table("a"));
        });
        cache.get(2L, () -> {
            calls.incrementAndGet();
            return List.of(table("b"));
        });
        cache.evict(1L);
        cache.get(1L, () -> {
            calls.incrementAndGet();
            return List.of(table("c"));
        });
        cache.get(2L, () -> {
            calls.incrementAndGet();
            return List.of(table("d"));
        });
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void clearInvalidatesEverything() {
        AtomicInteger calls = new AtomicInteger();
        cache.get(1L, () -> {
            calls.incrementAndGet();
            return List.of(table("a"));
        });
        cache.get(2L, () -> {
            calls.incrementAndGet();
            return List.of(table("b"));
        });
        cache.clear();
        cache.get(1L, () -> {
            calls.incrementAndGet();
            return List.of(table("c"));
        });
        cache.get(2L, () -> {
            calls.incrementAndGet();
            return List.of(table("d"));
        });
        assertThat(calls.get()).isEqualTo(4);
    }

    private static TableMetadata table(String name) {
        return new TableMetadata(name, List.of(), List.of(), List.of(), List.of(), null);
    }
}