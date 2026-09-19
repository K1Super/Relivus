package com.relivus.generator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 唯一约束检测器测试（冲突、LRU 淘汰、重置）。
 */
class UniqueConstraintCheckerTest {

    @Test
    void detectsDuplicateWithinGroup() {
        UniqueConstraintChecker checker = new UniqueConstraintChecker();
        assertThat(checker.checkAndAdd("g", "v1")).isFalse();
        assertThat(checker.checkAndAdd("g", "v1")).isTrue();
        assertThat(checker.checkAndAdd("g", "v2")).isFalse();
    }

    @Test
    void groupsAreIsolated() {
        UniqueConstraintChecker checker = new UniqueConstraintChecker();
        checker.checkAndAdd("g1", 1);
        assertThat(checker.checkAndAdd("g2", 1)).isFalse();
    }

    @Test
    void containsDoesNotRegister() {
        UniqueConstraintChecker checker = new UniqueConstraintChecker();
        assertThat(checker.contains("g", "x")).isFalse();
        assertThat(checker.checkAndAdd("g", "x")).isFalse();
        assertThat(checker.contains("g", "x")).isTrue();
        assertThat(checker.checkAndAdd("g", "other-key-for-same-value")).isFalse();
    }

    @Test
    void resetClearsState() {
        UniqueConstraintChecker checker = new UniqueConstraintChecker();
        checker.checkAndAdd("g", "v");
        checker.reset();
        assertThat(checker.checkAndAdd("g", "v")).isFalse();
    }

    @Test
    void overCapacityKeepsUniquenessSemantics() {
        // Caffeine 逐出为异步维护，不保证即时生效；此处仅验证容量超限不破坏唯一性语义
        UniqueConstraintChecker checker = new UniqueConstraintChecker(1);
        assertThat(checker.checkAndAdd("g", "a")).isFalse();
        assertThat(checker.checkAndAdd("g", "b")).isFalse();
        assertThat(checker.checkAndAdd("g", "c")).isFalse();
        assertThat(checker.checkAndAdd("g", "c")).isTrue();
    }
}