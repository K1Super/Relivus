package com.relivus.task;

/**
 * 任务执行体（由 Controller 根据任务类型组装）。
 *
 * <p>约定：异常（含取消的 RelivusException）不外抛，TaskService 负责捕获并终态化。
 */
@FunctionalInterface
public interface TaskJob {

    /** 执行任务体；{@code ctx} 提供进度/日志/取消能力。 */
    void run(TaskContext ctx);
}