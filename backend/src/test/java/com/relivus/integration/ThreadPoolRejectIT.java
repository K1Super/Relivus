package com.relivus.integration;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.config.TaskExecutorConfig;
import com.relivus.config.RelivusProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 线程池拒绝策略集成测试（DOC-08 / 5004）。
 *
 * <p>核心 1 / 最大 1 / 队列 0：首个任务占满唯一工作线程后，新任务无法入队且无空闲
 * 线程，触发自定义拒绝处理器抛 {@code RELIVUS_TASK_QUEUE_FULL}（5004）。
 */
class ThreadPoolRejectIT {

    @Test
    void taskSubmissionRejectsWhenPoolExhausted() throws Exception {
        RelivusProperties props = new RelivusProperties();
        props.getTask().setCorePoolSize(1);
        props.getTask().setMaxPoolSize(1);
        props.getTask().setQueueCapacity(0);
        ThreadPoolTaskExecutor executor = new TaskExecutorConfig().taskExecutor(props);

        CountDownLatch blocker = new CountDownLatch(1);
        CountDownLatch running = new CountDownLatch(1);
        executor.execute(() -> {
            running.countDown();
            try {
                blocker.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        boolean started = running.await(5, TimeUnit.SECONDS);
        assertThat(started).isTrue();

        try {
            Runnable rejected = () -> {
            };
            assertThatThrownBy(() -> executor.execute(rejected))
                    .isInstanceOf(RelivusException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.TASK_QUEUE_FULL.getCode());
        } finally {
            blocker.countDown();
            executor.shutdown();
        }
    }
}