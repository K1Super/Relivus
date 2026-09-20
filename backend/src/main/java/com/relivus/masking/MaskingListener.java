package com.relivus.masking;

/**
 * 脱敏引擎运行监听器（任务进度回调，脱敏侧）。
 *
 * <p>由任务执行层（ITaskService）桥接为进度日志与 SSE 推送。实现必须线程安全且不阻塞主循环。
 */
public interface MaskingListener {

    /** 单表进度回调。 */
    void onProgress(String table, long processedRows, long totalRows);

    /** 运行日志回调。 */
    void onLog(Severity severity, String message);

    /** 取消令牌：引擎每批校验。 */
    boolean isCancelled();

    enum Severity {
        INFO, WARN, ERROR
    }
}