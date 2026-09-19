package com.relivus.task;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 任务进度 SSE 端点。
 *
 * <pre>
 * GET /api/tasks/{id}/progress   Accept: text/event-stream   Authorization: Bearer ...
 * </pre>
 *
 * <p>连接超时 30 分钟；全局连接数上限 20（超限抛异常）；心跳由
 * {@link SseTaskProgressNotifier#heartbeat()} 每 15 秒推送。鉴权由全局过滤器统一校验。
 */
@RestController
public class SseProgressController {

    /** SSE 连接超时（毫秒）：30 分钟。 */
    private static final long SSE_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final TaskService taskService;
    private final SseTaskProgressNotifier notifier;

    public SseProgressController(TaskService taskService, SseTaskProgressNotifier notifier) {
        this.taskService = taskService;
        this.notifier = notifier;
    }

    @GetMapping(value = "/api/tasks/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter progress(@PathVariable Long id) {
        taskService.getTask(id); // 任务不存在时抛异常
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        notifier.register(id, emitter);
        // 连接建立即推送一次元数据，前端可据此立即点亮进度条
        notifier.log(id, "INFO", "SSE 连接已建立");
        return emitter;
    }
}