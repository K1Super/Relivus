package com.relivus.task;

/**
 * 任务状态机。
 *
 * <pre>
 * PENDING → RUNNING → SUCCESS
 *                  → FAILED
 *                  → CANCELLED
 * </pre>
 *
 * <p>PENDING：已入队；RUNNING：线程池执行中；终态：SUCCESS / FAILED / CANCELLED。
 * 取消仅对 PENDING 与 RUNNING 有效。
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED;

    /** 是否已进入终态（结束后不可再取消）。 */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }
}