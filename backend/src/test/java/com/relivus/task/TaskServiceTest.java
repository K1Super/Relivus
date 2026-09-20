package com.relivus.task;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.config.TaskExecutorConfig;
import com.relivus.entity.TaskEntity;
import com.relivus.repository.TaskLogRepository;
import com.relivus.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务服务测试（创建/状态流转/取消）。线程池 submit 同步执行以验证状态机。
 */
class TaskServiceTest {

    private TaskRepository taskRepository;
    private TaskLogRepository taskLogRepository;
    private ThreadPoolTaskExecutor taskExecutor;
    private TaskProgressNotifier notifier;
    private ITaskService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        taskLogRepository = mock(TaskLogRepository.class);
        taskExecutor = mock(ThreadPoolTaskExecutor.class);
        notifier = mock(TaskProgressNotifier.class);
        service = new TaskServiceImpl(taskRepository, taskLogRepository, taskExecutor, notifier);
    }

    /** 让 submit 同步执行 Runnable（ThreadPoolTaskExecutor 真实执行体是 private）。 */
    private void syncSubmit() {
        doAnswer(inv -> {
            Runnable task = inv.getArgument(0);
            task.run();
            return mock(Future.class);
        }).when(taskExecutor).submit(any(Runnable.class));
    }

    @Test
    void createTaskPersistsPendingEntityAndReturnsId() {
        when(taskRepository.insert(any(TaskEntity.class))).thenReturn(7L);
        Long id = service.createTask("generation", 3L, "{\"tables\":[]}");
        assertThat(id).isEqualTo(7L);

        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(captor.getValue().getTaskType()).isEqualTo("generation");
        assertThat(captor.getValue().getConnectionId()).isEqualTo(3L);
    }

    @Test
    void successfulJobTransitionsToSuccess() {
        syncSubmit();
        service.executeAsync(5L, ctx -> {
            ctx.progress(50, 100, 200);
            ctx.log("INFO", "hello");
        });
        verify(taskRepository).markRunning(eq(5L), any(LocalDateTime.class));
        verify(taskRepository).updateStatus(eq(5L), eq(TaskStatus.SUCCESS), eq(100), any(), any(LocalDateTime.class));
        verify(taskRepository).updateProgress(5L, 50, 100);
        verify(taskLogRepository).insert(any(com.relivus.entity.TaskLogEntity.class));
        verify(notifier).progress(eq(5L), eq(50), eq(100L), eq(200L));
        verify(notifier).log(eq(5L), eq("INFO"), eq("hello"));
        verify(notifier).done(eq(5L), eq("SUCCESS"));
    }

    @Test
    void failingJobTransitionsToFailed() {
        syncSubmit();
        service.executeAsync(5L, ctx -> {
            throw new RelivusException(ErrorCode.GENERATION_FAILED, "gen boom");
        });
        verify(taskRepository).updateStatus(eq(5L), eq(TaskStatus.FAILED), eq(0), eq("gen boom"), any(LocalDateTime.class));
        verify(notifier).error(5L, "gen boom");
        verify(notifier, never()).done(anyLong(), anyString());
    }

    @Test
    void unexpectedExceptionTransitionsToFailed() {
        syncSubmit();
        service.executeAsync(5L, ctx -> {
            throw new IllegalStateException("boom");
        });
        verify(taskRepository).updateStatus(eq(5L), eq(TaskStatus.FAILED), eq(0), eq("boom"), any(LocalDateTime.class));
        verify(notifier).error(5L, "boom");
    }

    @Test
    void jobObservingDbCancelFlagTransitionsToCancelled() {
        when(taskRepository.isCancelRequested(5L)).thenReturn(true);
        syncSubmit();
        service.executeAsync(5L, ctx -> {
            if (ctx.isCancelled()) {
                throw new RelivusException(ErrorCode.TASK_CANCEL_FAILED, "任务已取消");
            }
        });
        verify(taskRepository).updateStatus(eq(5L), eq(TaskStatus.CANCELLED), eq(0), eq("任务已取消"), any(LocalDateTime.class));
        verify(notifier).done(5L, "CANCELLED");
    }

    @Test
    void cancelMissingTaskThrows5001() {
        when(taskRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(9L))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.TASK_NOT_FOUND.getCode());
    }

    @Test
    void cancelTerminalTaskThrows5002() {
        TaskEntity success = entity(TaskStatus.SUCCESS);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(success));
        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.TASK_CANCEL_FAILED.getCode());
    }

    @Test
    void cancelRunningTaskMarksAndInterrupts() {
        TaskEntity running = entity(TaskStatus.RUNNING);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(running));
        syncSubmit();

        boolean result = service.cancel(5L);
        assertThat(result).isTrue();
        verify(taskRepository).markCancelRequested(5L);
        verify(taskRepository, never()).updateStatus(eq(5L), eq(TaskStatus.CANCELLED), anyInt(), any(), any());
        verify(notifier, never()).done(anyLong(), anyString());
    }

    @Test
    void cancelPendingTaskMarksCancelledTerminalState() {
        TaskEntity pending = entity(TaskStatus.PENDING);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(pending));
        syncSubmit();

        boolean result = service.cancel(5L);
        assertThat(result).isTrue();
        verify(taskRepository).markCancelRequested(5L);
        verify(taskRepository).updateStatus(eq(5L), eq(TaskStatus.CANCELLED), eq(0), eq("用户取消"), any(LocalDateTime.class));
        verify(notifier).done(5L, "CANCELLED");
    }

    @Test
    void getTaskAndListTasksDelegateToRepository() {
        TaskEntity task = entity(TaskStatus.RUNNING);
        when(taskRepository.findById(3L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());
        when(taskRepository.findAll(20, 0)).thenReturn(List.of(task));

        assertThat(service.getTask(3L)).isSameAs(task);
        assertThatThrownBy(() -> service.getTask(99L))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.TASK_NOT_FOUND.getCode());
        assertThat(service.listTasks(20, 0)).containsExactly(task);
        verify(taskRepository).findAll(20, 0);
    }

    @Test
    void updateDataBaselineDelegatesToRepository() {
        service.updateDataBaseline(5L, "{\"users\":3}");
        verify(taskRepository).updateDataBaseline(5L, "{\"users\":3}");
    }

    private static TaskEntity entity(TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setId(5L);
        task.setTaskType("generation");
        task.setConnectionId(3L);
        task.setStatus(status);
        task.setProgress(0);
        return task;
    }
}