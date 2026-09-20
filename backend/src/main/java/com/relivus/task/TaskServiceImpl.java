package com.relivus.task;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.config.TaskExecutorConfig;
import com.relivus.entity.TaskEntity;
import com.relivus.entity.TaskLogEntity;
import com.relivus.repository.TaskLogRepository;
import com.relivus.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务服务（取消设计）。
 *
 * <p>职责：任务创建、异步提交（线程池拒绝）、状态机流转、取消（内存 AtomicBoolean +
 * 持久化 {@code df_task.cancel_requested} 双标志）、进度/日志落库与 SSE 推送桥接。
 * 自身不含业务执行逻辑（入口零业务逻辑），业务由 {@link TaskJob} 在 controller 层组装。
 */
@Service
public class TaskServiceImpl implements ITaskService {

    private static final Logger LOG = LoggerFactory.getLogger(TaskServiceImpl.class);

    private final TaskRepository taskRepository;
    private final TaskLogRepository taskLogRepository;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final TaskProgressNotifier notifier;

    /** 运行中任务：taskId → Future（供中断）。 */
    private final Map<Long, Future<?>> running = new ConcurrentHashMap<>();
    /** 运行中任务：taskId → 取消标志（引擎每批轮询）。 */
    private final Map<Long, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public TaskServiceImpl(TaskRepository taskRepository, TaskLogRepository taskLogRepository,
                       @Qualifier(TaskExecutorConfig.TASK_EXECUTOR) ThreadPoolTaskExecutor taskExecutor,
                       TaskProgressNotifier notifier) {
        this.taskRepository = taskRepository;
        this.taskLogRepository = taskLogRepository;
        this.taskExecutor = taskExecutor;
        this.notifier = notifier;
    }

    /** 创建任务（PENDING），返回 taskId。configJson 为执行配置 JSON（含敏感信息须先脱敏或存引用）。 */
    @Override
    public Long createTask(String taskType, Long connectionId, String configJson) {
        TaskEntity entity = new TaskEntity();
        entity.setTaskType(taskType);
        entity.setConnectionId(connectionId);
        entity.setConfigJson(configJson);
        entity.setStatus(TaskStatus.PENDING);
        entity.setProgress(0);
        entity.setTotalRows(0);
        entity.setProcessedRows(0);
        entity.setCancelRequested(false);
        Long id = taskRepository.insert(entity);
        LOG.info("Task created, id={}, type={}, connectionId={}", id, taskType, connectionId);
        return id;
    }

    /**
     * 异步执行任务。
     *
     * <p>线程池拒绝（队列满）时抛异常。状态流转：
     * PENDING → RUNNING → SUCCESS / FAILED / CANCELLED。
     */
    @Override
    public void executeAsync(Long taskId, TaskJob job) {
        AtomicBoolean flag = new AtomicBoolean(false);
        cancelFlags.put(taskId, flag);
        Future<?> future;
        try {
            future = taskExecutor.submit(() -> runTask(taskId, flag, job));
        } catch (RelivusException e) {
            cancelFlags.remove(taskId);
            throw e;
        }
        running.put(taskId, future);
    }

    /** 取消任务：持久化 cancel_requested + 内存标志 + 中断 Future。 */
    @Override
    public boolean cancel(Long taskId) {
        TaskEntity entity = requireTask(taskId);
        if (entity.getStatus().isTerminal()) {
            throw new RelivusException(ErrorCode.TASK_CANCEL_FAILED,
                    "任务已结束（" + entity.getStatus() + "），无法取消");
        }
        taskRepository.markCancelRequested(taskId);
        AtomicBoolean flag = cancelFlags.get(taskId);
        if (flag != null) {
            flag.set(true);
        }
        Future<?> future = running.get(taskId);
        if (future != null) {
            future.cancel(true);
        }
        // 任务尚未真正开始（排队中）：直接置 CANCELLED 终态，避免悬在 PENDING
        TaskEntity current = taskRepository.findById(taskId)
                .orElse(entity);
        if (current.getStatus() == TaskStatus.PENDING) {
            taskRepository.updateStatus(taskId, TaskStatus.CANCELLED, 0, "用户取消", LocalDateTime.now());
            notifier.done(taskId, TaskStatus.CANCELLED.name());
            running.remove(taskId);
            cancelFlags.remove(taskId);
        }
        LOG.info("Task cancel requested, id={}", taskId);
        return true;
    }

    @Override
    public TaskEntity getTask(Long taskId) {
        return requireTask(taskId);
    }

    /** 写入生成数据回看基线 JSON（执行开始前由生成流程采集后调用）。 */
    @Override
    public void updateDataBaseline(Long taskId, String dataBaselineJson) {
        taskRepository.updateDataBaseline(taskId, dataBaselineJson);
    }

    @Override
    public List<TaskEntity> listTasks(int limit, int offset) {
        return taskRepository.findAll(limit, offset);
    }

    private TaskEntity requireTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new RelivusException(ErrorCode.TASK_NOT_FOUND, "任务不存在：" + taskId));
    }

    /** 线程内执行体：状态机 + 异常终态化 + 资源清理。 */
    private void runTask(Long taskId, AtomicBoolean flag, TaskJob job) {
        TaskRunner runner = new TaskRunner(taskId, flag);
        try {
            if (flag.get()) {
                throw new RelivusException(ErrorCode.TASK_CANCEL_FAILED, "任务已取消");
            }
            taskRepository.markRunning(taskId, LocalDateTime.now());
            job.run(runner.getContext());
            finishSuccess(taskId, runner);
        } catch (RelivusException e) {
            // 取消双标志（内存 + DB 落库）：引擎检测到取消时 flag 可能未置位，按错误码兜底
            if (flag.get() || e.getErrorCode() == ErrorCode.TASK_CANCEL_FAILED) {
                finishCancelled(taskId, runner, e.getMessage());
            } else {
                finishFailed(taskId, runner, e.getMessage());
            }
        } catch (Exception e) {
            LOG.error("Task execution error, id=" + taskId, e);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            finishFailed(taskId, runner, message);
        } finally {
            running.remove(taskId);
            cancelFlags.remove(taskId);
        }
    }

    private void finishSuccess(Long taskId, TaskRunner runner) {
        taskRepository.updateStatus(taskId, TaskStatus.SUCCESS, 100, null, LocalDateTime.now());
        runner.flushFinal(true);
        notifier.done(taskId, TaskStatus.SUCCESS.name());
        LOG.info("Task finished, id={}, status=SUCCESS", taskId);
    }

    private void finishCancelled(Long taskId, TaskRunner runner, String message) {
        taskRepository.updateStatus(taskId, TaskStatus.CANCELLED, 0,
                message == null ? "用户取消" : message, LocalDateTime.now());
        runner.flushFinal(false);
        notifier.done(taskId, TaskStatus.CANCELLED.name());
        LOG.info("Task cancelled, id={}", taskId);
    }

    private void finishFailed(Long taskId, TaskRunner runner, String message) {
        String error = message == null ? "未知错误" : (message.length() > 2000 ? message.substring(0, 2000) : message);
        taskRepository.updateStatus(taskId, TaskStatus.FAILED, 0, error, LocalDateTime.now());
        runner.flushFinal(false);
        notifier.error(taskId, error);
        LOG.warn("Task failed, id={}, error={}", taskId, error);
    }

    /**
     * 任务执行桥接器：实现 {@link TaskContext}、生成引擎与脱敏引擎监听器。
     *
     * <p>多表进度汇总：逐表「已处理/总量」累计后折算总体百分比；写库节流（百分比变化或 1 秒）,
     * SSE 推送仅在百分比变化时发出。
     */
    private final class TaskRunner {

        private final Long taskId;
        private final AtomicBoolean flag;
        private final Map<String, long[]> stats = new ConcurrentHashMap<>();
        private final long INIT_TIME = System.currentTimeMillis();
        private volatile long lastDbWriteMs;
        private volatile int lastPercent = -1;

        private TaskRunner(Long taskId, AtomicBoolean flag) {
            this.taskId = taskId;
            this.flag = flag;
        }

        private final TaskContext ctx = new TaskContext() {
            @Override
            public void progress(int percent, long processed, long total) {
                recordProgress(percent, processed, total);
            }

            @Override
            public void log(String level, String message) {
                persistLog(level, message);
            }

            @Override
            public boolean isCancelled() {
                return TaskRunner.this.isCancelled();
            }

            @Override
            public com.relivus.generator.GenerationEngineListener generationListener() {
                return new com.relivus.generator.GenerationEngineListener() {
                    @Override
                    public void onProgress(String table, long insertedRows, long totalRows) {
                        stats.merge(table, new long[]{insertedRows, Math.max(totalRows, insertedRows)},
                                (old, v) -> new long[]{v[0], Math.max(old[1], v[1])});
                        refresh();
                    }

                    @Override
                    public void onLog(com.relivus.generator.GenerationEngineListener.Severity severity, String message) {
                        persistLog(severity.name(), message);
                    }

                    @Override
                    public boolean isCancelled() {
                        return TaskRunner.this.isCancelled();
                    }
                };
            }

            @Override
            public com.relivus.masking.MaskingListener maskingListener() {
                return new com.relivus.masking.MaskingListener() {
                    @Override
                    public void onProgress(String table, long processedRows, long totalRows) {
                        stats.merge(table, new long[]{processedRows, Math.max(totalRows, processedRows)},
                                (old, v) -> new long[]{v[0], Math.max(old[1], v[1])});
                        refresh();
                    }

                    @Override
                    public void onLog(com.relivus.masking.MaskingListener.Severity severity, String message) {
                        persistLog(severity.name(), message);
                    }

                    @Override
                    public boolean isCancelled() {
                        return TaskRunner.this.isCancelled();
                    }
                };
            }
        };

        TaskContext getContext() {
            return ctx;
        }

        boolean isCancelled() {
            if (flag.get()) {
                return true;
            }
            // 持久化标志双保险；DB 检查节流 200ms
            long now = System.currentTimeMillis();
            if (now - lastDbWriteMs < 200) {
                return false;
            }
            lastDbWriteMs = now;
            return taskRepository.isCancelRequested(taskId);
        }

        /** 汇总各表进度并折算总体百分比。 */
        void refresh() {
            long sumProcessed = 0;
            long sumTotal = 0;
            for (long[] s : stats.values()) {
                sumProcessed += s[0];
                sumTotal += s[1];
            }
            int percent = (int) (sumTotal == 0 ? 0 : Math.min(100, sumProcessed * 100.0 / sumTotal));
            recordProgress(percent, sumProcessed, sumTotal);
        }

        void recordProgress(int percent, long processed, long total) {
            int p = Math.max(0, Math.min(100, percent));
            long now = System.currentTimeMillis();
            boolean percentChanged = p != lastPercent;
            boolean forceDbWrite = now - lastDbWriteMs >= 1000;
            if (percentChanged || forceDbWrite) {
                safe(() -> taskRepository.updateProgress(taskId, p, processed));
                lastDbWriteMs = now;
            }
            if (percentChanged) {
                lastPercent = p;
                safe(() -> notifier.progress(taskId, p, processed, total));
            }
        }

        void persistLog(String level, String message) {
            TaskLogEntity log = new TaskLogEntity();
            log.setTaskId(taskId);
            log.setLevel(level == null || level.isBlank() ? "INFO" : level);
            log.setMessage(message);
            safe(() -> taskLogRepository.insert(log));
            safe(() -> notifier.log(taskId, log.getLevel(), message));
        }

        /** 任务结束时的兜底刷新（确保日志不丢失）。 */
        void flushFinal(boolean forceProgress) {
            if (forceProgress) {
                long sumProcessed = 0;
                for (long[] s : stats.values()) {
                    sumProcessed += s[0];
                }
                final long finalProcessed = sumProcessed;
                safe(() -> taskRepository.updateProgress(taskId, 100, finalProcessed));
            }
        }

        private void safe(Runnable action) {
            try {
                action.run();
            } catch (Exception e) {
                LOG.warn("Task bookkeeping failed, id={}: {}", taskId, e.getMessage());
            }
        }
    }
}