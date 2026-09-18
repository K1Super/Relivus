package com.relivus.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE 进度推送器（DOC-11.8 按任务分组）。
 *
 * <p>连接管理：{@code Map<Long, Set<SseEmitter>>} 按 taskId 分组；全局连接数上限 20（超限抛 5003）；
 * 超时 30 分钟（SseEmitter 构造参数）；心跳每 15 秒一次（{@code heartbeat} 事件，负载 "{}"）。
 * 断线自动清理：onCompletion / onTimeout / onError 移除对应连接。
 */
@Component
public class SseTaskProgressNotifier implements TaskProgressNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(SseTaskProgressNotifier.class);

    /** 全局最大 SSE 连接数（DOC-05 / DOC-11.8）。 */
    private static final int MAX_CONNECTIONS = 20;

    private final Map<Long, Set<SseEmitter>> emittersByTask = new ConcurrentHashMap<>();
    private final AtomicInteger connectionCount = new AtomicInteger();
    private final ObjectMapper objectMapper;

    public SseTaskProgressNotifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 注册任务连接。全局并发连接达到 20 时拒绝并抛 5003。
     */
    public void register(Long taskId, SseEmitter emitter) {
        synchronized (emittersByTask) {
            if (connectionCount.get() >= MAX_CONNECTIONS) {
                emitter.complete();
                throw new RelivusException(ErrorCode.SSE_LIMIT_EXCEEDED,
                        "SSE connection limit reached (max " + MAX_CONNECTIONS + ")");
            }
            Set<SseEmitter> set = emittersByTask.computeIfAbsent(taskId, k -> ConcurrentHashMap.newKeySet());
            set.add(emitter);
            connectionCount.incrementAndGet();
            emitter.onCompletion(() -> remove(taskId, emitter));
            emitter.onTimeout(() -> remove(taskId, emitter));
            emitter.onError(e -> remove(taskId, emitter));
        }
    }

    @Override
    public void progress(Long taskId, int progress, long processed, long total) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("progress", progress);
        data.put("processed", processed);
        data.put("total", total);
        sendToTask(taskId, "progress", data);
    }

    @Override
    public void log(Long taskId, String level, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("level", level);
        data.put("message", message);
        sendToTask(taskId, "log", data);
    }

    @Override
    public void done(Long taskId, String status) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("status", status);
        sendToTask(taskId, "done", data);
        removeTask(taskId);
    }

    @Override
    public void error(Long taskId, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("message", message);
        sendToTask(taskId, "error", data);
        removeTask(taskId);
    }

    /** 心跳：每 15 秒对所有存活连接发送 heartbeat 事件（DOC-05）。 */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        if (emittersByTask.isEmpty()) {
            return;
        }
        for (Set<SseEmitter> set : emittersByTask.values()) {
            for (SseEmitter emitter : set) {
                send(emitter, "heartbeat", "{}");
            }
        }
    }

    private void sendToTask(Long taskId, String event, Object payload) {
        Set<SseEmitter> set = emittersByTask.get(taskId);
        if (set == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            for (SseEmitter emitter : set) {
                send(emitter, event, json);
            }
        } catch (IOException e) {
            LOG.warn("Serialize SSE event failed, taskId={}, event={}", taskId, event);
        }
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            LOG.debug("SSE send failed, removing emitter: {}", e.getMessage());
            emitter.completeWithError(e);
        }
    }

    private void remove(Long taskId, SseEmitter emitter) {
        Set<SseEmitter> set = emittersByTask.get(taskId);
        if (set != null && set.remove(emitter)) {
            connectionCount.decrementAndGet();
        }
        if (set != null && set.isEmpty()) {
            emittersByTask.remove(taskId);
        }
    }

    private void removeTask(Long taskId) {
        Set<SseEmitter> set = emittersByTask.remove(taskId);
        if (set != null) {
            set.forEach(SseEmitter::complete);
            connectionCount.addAndGet(-set.size());
        }
    }
}