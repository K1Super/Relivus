package com.relivus.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务状态机测试（DOC-05：终态判定）。
 */
class TaskStatusTest {

    @Test
    void onlyTerminalStatesAreFinished() {
        assertThat(TaskStatus.PENDING.isTerminal()).isFalse();
        assertThat(TaskStatus.RUNNING.isTerminal()).isFalse();
        assertThat(TaskStatus.SUCCESS.isTerminal()).isTrue();
        assertThat(TaskStatus.FAILED.isTerminal()).isTrue();
        assertThat(TaskStatus.CANCELLED.isTerminal()).isTrue();
    }
}