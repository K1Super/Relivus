package com.relivus.generator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 本地唯一值检测（DOC-11.2：唯一检测内存控制）。
 *
 * <p>第一层为数据库唯一约束兜底，本类为第二层：本地仅缓存最近 N 个值（LRU，默认 10000），
 * 避免百万行场景 {@code Map<列组, Set<值>>} 导致 OOM。
 */
public class UniqueConstraintChecker {

    /** 默认本地缓存容量。 */
    public static final int DEFAULT_CAPACITY = 10000;

    private final Cache<String, Boolean> recentValues;

    public UniqueConstraintChecker() {
        this(DEFAULT_CAPACITY);
    }

    public UniqueConstraintChecker(int capacity) {
        this.recentValues = Caffeine.newBuilder().maximumSize(capacity).build();
    }

    /**
     * 检测并登记：返回 true 表示该值已存在（冲突），false 表示可安全使用并已登记。
     */
    public boolean checkAndAdd(String group, Object value) {
        String key = key(group, value);
        if (recentValues.getIfPresent(key) != null) {
            return true;
        }
        recentValues.put(key, Boolean.TRUE);
        return false;
    }

    /** 仅检测是否冲突，不登记。 */
    public boolean contains(String group, Object value) {
        return recentValues.getIfPresent(key(group, value)) != null;
    }

    /** 任务结束后清理本地状态，防止跨任务串扰。 */
    public void reset() {
        recentValues.invalidateAll();
    }

    private static String key(String group, Object value) {
        return group + "\u0001" + String.valueOf(value);
    }
}