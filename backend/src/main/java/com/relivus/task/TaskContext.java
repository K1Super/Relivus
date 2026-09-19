package com.relivus.task;

import com.relivus.generator.GenerationEngineListener;
import com.relivus.masking.MaskingListener;
import com.relivus.masking.MaskingListener.Severity;

/**
 * 任务执行上下文（由 TaskService 注入给 {@link TaskJob}）。
 *
 * <p>为生成/脱敏引擎提供进度、日志与取消令牌的桥接；同时暴露两类引擎监听器，
 * 引擎每批处理后回调即更新任务状态与 SSE 推送。
 */
public interface TaskContext {

    /** 汇报总体进度（百分比 0-100、已处理行数、总行数），落库并推送。 */
    void progress(int percent, long processed, long total);

    /** 写入任务日志（INFO/WARN/ERROR），落库并推送。 */
    void log(String level, String message);

    /** 取消令牌：引擎每批处理校验，返回 true 时引擎中止并抛取消失败异常。 */
    boolean isCancelled();

    /** 生成引擎监听器（桥接 {@link #progress} / {@link #log} / {@link #isCancelled}）。 */
    GenerationEngineListener generationListener();

    /** 脱敏引擎监听器。 */
    MaskingListener maskingListener();

    /** Severity → 字符串级别，供统一日志装配。 */
    static String levelOf(Severity severity) {
        return severity == null ? "INFO" : severity.name();
    }
}