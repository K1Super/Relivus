package com.relivus.common.api;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 幂等键守卫（POST /execute 支持 Idempotency-Key）。
 *
 * <p>同一 Idempotency-Key 在 TTL（24 小时）内返回首次创建的任务 ID，防止客户端重复提交
 * 生成/脱敏任务。实现为进程内存储（单实例部署语义），多实例横向扩展时需改由元数据库承载，
 * 限制在实现注释中说明。
 */
@Component
public class IdempotencyGuard {

    /** 幂等记录有效期（毫秒）：24 小时。 */
    private static final long TTL_MILLIS = 24 * 60 * 60 * 1000L;

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * 幂等获取任务 ID。
     *
     * @param key        Idempotency-Key；为空则直接走 {@code supplier}
     * @param supplier   首次提交时创建任务并返回 taskId
     * @return 已存在则返回历史 taskId，否则返回新建 taskId
     */
    public Long getOrCreate(String key, Supplier<Long> supplier) {
        if (key == null || key.isBlank()) {
            return supplier.get();
        }
        long now = System.currentTimeMillis();
        Entry existing = entries.get(key);
        if (existing != null && existing.expiresAt > now) {
            return existing.taskId;
        }
        Long taskId = supplier.get();
        entries.put(key, new Entry(taskId, now + TTL_MILLIS));
        return taskId;
    }

    /** 惰性清理过期条目（每次写入后触发精简清理）。 */
    public void sweep() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(e -> e.getValue().expiresAt <= now);
    }

    private record Entry(long taskId, long expiresAt) {
    }
}