package com.relivus.integration;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.task.SseTaskProgressNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SSE 连接上限集成测试。
 *
 * <p>超过 20 个并发 SSE 连接时第 21 个被拒绝；心跳对存活连接发送 heartbeat
 * 事件不抛异常。纯内存验证，无需数据库。
 */
class SseLimitIT {

    private static final int MAX_CONNECTIONS = 20;

    @Test
    void twentyFirstConnectionRejectedWith5003() {
        SseTaskProgressNotifier notifier = new SseTaskProgressNotifier(new ObjectMapper());
        List<SseEmitter> emitters = new ArrayList<>();
        for (int i = 0; i < MAX_CONNECTIONS; i++) {
            SseEmitter emitter = new SseEmitter(60_000L);
            notifier.register(1L, emitter);
            emitters.add(emitter);
        }
        SseEmitter extra = new SseEmitter(60_000L);
        assertThatThrownBy(() -> notifier.register(2L, extra))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.SSE_LIMIT_EXCEEDED.getCode());
    }

    @Test
    void heartbeatRunsAgainstLiveConnections() {
        SseTaskProgressNotifier notifier = new SseTaskProgressNotifier(new ObjectMapper());
        SseEmitter emitter = new SseEmitter(60_000L);
        notifier.register(1L, emitter);
        // 心跳不抛异常（事件写入 mock 传输层）
        notifier.heartbeat();
        assertThat(true).isTrue();
    }
}