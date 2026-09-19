package com.relivus.task;

/**
 * 任务进度推送接口（按任务分组推送）。
 *
 * <p>TaskService 在更新进度、写日志、完成、失败、取消时调用；SSE 实现按 taskId
 * 从连接组取 SseEmitter 推送对应事件。接口与事件载荷对应。
 */
public interface TaskProgressNotifier {

    /** progress 事件：{@code {"taskId":1,"progress":50,"processed":5000,"total":10000}} */
    void progress(Long taskId, int progress, long processed, long total);

    /** log 事件：{@code {"level":"INFO","message":"..."}} */
    void log(Long taskId, String level, String message);

    /** done 事件：{@code {"taskId":1,"status":"SUCCESS"}} */
    void done(Long taskId, String status);

    /** error 事件：{@code {"taskId":1,"message":"..."}} */
    void error(Long taskId, String message);
}