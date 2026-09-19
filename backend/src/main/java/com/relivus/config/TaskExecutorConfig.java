package com.relivus.config;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 任务线程池配置。
 *
 * <p>核心 4 / 最大 8 / 队列 100，专用于生成与脱敏任务。拒绝策略为抛 {@code RelivusException(150004)}
 * （任务队列已满），不静默丢弃、不使用 CallerRunsPolicy。
 *
 * <p>{@link TaskDecorator} 将主线程的 MDC（traceId 等）传递到任务线程，并在任务结束时清理，
 * 保证异步链路的日志可追溯且不残留线程上下文。
 */
@Configuration
public class TaskExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutorConfig.class);

    /** 任务执行器 Bean 名称。 */
    public static final String TASK_EXECUTOR = "taskExecutor";

    @Bean(TASK_EXECUTOR)
    public ThreadPoolTaskExecutor taskExecutor(RelivusProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getTask().getCorePoolSize());
        executor.setMaxPoolSize(props.getTask().getMaxPoolSize());
        executor.setQueueCapacity(props.getTask().getQueueCapacity());
        executor.setThreadNamePrefix("relivus-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler((r, exec) -> {
            log.warn("Task rejected, queue={}, active={}", exec.getQueue().size(), exec.getActiveCount());
            throw new RelivusException(ErrorCode.TASK_QUEUE_FULL, "Task queue full, retry later");
        });
        executor.initialize();
        return executor;
    }
}