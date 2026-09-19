package com.relivus.generator;

/**
 * 生成引擎运行监听器（任务进度回调）。
 *
 * <p>引擎在批量完成、异常、取消等节点回调，由任务执行层（TaskService）桥接为
 * 进度日志与 SSE 推送。实现必须线程安全且不阻塞生成主循环。
 */
public interface GenerationEngineListener {

    /** 单表单批进度回调。 */
    void onProgress(String table, long insertedRows, long totalRows);

    /** 运行日志回调（关键节点，供任务日志落库）。 */
    void onLog(Severity severity, String message);

    /** 取消令牌：引擎每批校验，返回 true 时中止并抛任务取消异常。 */
    boolean isCancelled();

    /** 日志级别（任务日志与 SSE 共用）。 */
    enum Severity {
        INFO, WARN, ERROR
    }
}