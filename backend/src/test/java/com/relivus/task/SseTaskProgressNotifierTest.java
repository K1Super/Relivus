package com.relivus.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * SSE 进度推送器测试（DOC-08：连接上限 5003、事件名、心跳、断开清理）。
 *
 * <p>SseEmitter.SseEventBuilder#build() 在 Spring 6.2 中返回
 * {@code Set<ResponseBodyEmitter.DataWithMediaType>}，因此事件名与负载统一从
 * DataWithMediaType#getData() 的渲染文本中断言。
 */
class SseTaskProgressNotifierTest {

    private final SseTaskProgressNotifier notifier = new SseTaskProgressNotifier(new ObjectMapper());

    /** 拼接 builder 渲染出的全部 SSE 文本，便于断言事件名与负载。 */
    private static String sseText(SseEmitter.SseEventBuilder builder) {
        StringBuilder sb = new StringBuilder();
        for (Object item : builder.build()) {
            sb.append(((ResponseBodyEmitter.DataWithMediaType) item).getData());
        }
        return sb.toString();
    }

    @Test
    void registerAndPushProgressEvent() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        notifier.register(1L, emitter);
        notifier.progress(1L, 50, 100, 200);

        ArgumentCaptor<SseEmitter.SseEventBuilder> captor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(captor.capture());
        String text = sseText(captor.getValue());
        assertThat(text).contains("event:progress");
        assertThat(text).contains("\"progress\":50").contains("\"total\":200");
    }

    @Test
    void logAndErrorAndDoneEvents() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        notifier.register(1L, emitter);

        notifier.log(1L, "WARN", "careful");
        notifier.error(1L, "failed");

        ArgumentCaptor<SseEmitter.SseEventBuilder> captor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter, times(2)).send(captor.capture());
        boolean sawLog = false;
        boolean sawError = false;
        for (SseEmitter.SseEventBuilder builder : captor.getAllValues()) {
            String text = sseText(builder);
            if (text.contains("event:log") && text.contains("\"level\":\"WARN\"")) {
                sawLog = true;
            }
            if (text.contains("event:error") && text.contains("\"message\":\"failed\"")) {
                sawError = true;
            }
        }
        assertThat(sawLog).isTrue();
        assertThat(sawError).isTrue();
        verify(emitter).complete(); // error 后 removeTask
    }

    @Test
    void doneRemovesTaskFromGroup() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        notifier.register(1L, emitter);
        notifier.done(1L, "SUCCESS");

        ArgumentCaptor<SseEmitter.SseEventBuilder> captor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(captor.capture());
        assertThat(sseText(captor.getValue())).contains("event:done");

        // 任务组清空后再推送不应再触发 send
        notifier.progress(1L, 60, 1, 1);
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    void rejectsConnectionBeyondLimitWith5003() {
        for (int i = 0; i < 20; i++) {
            notifier.register((long) i, mock(SseEmitter.class));
        }
        assertThatThrownBy(() -> notifier.register(999L, mock(SseEmitter.class)))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.SSE_LIMIT_EXCEEDED.getCode());
    }

    @Test
    void heartbeatSendsToRegisteredEmittersAndNoopsWhenEmpty() throws Exception {
        notifier.heartbeat(); // 空注册表不抛

        SseEmitter emitter = mock(SseEmitter.class);
        notifier.register(1L, emitter);
        notifier.heartbeat();

        ArgumentCaptor<SseEmitter.SseEventBuilder> captor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(captor.capture());
        assertThat(sseText(captor.getValue())).contains("event:heartbeat");
    }

    @Test
    void pushToUnknownTaskIsNoop() {
        notifier.progress(404L, 10, 1, 1);
        notifier.log(404L, "INFO", "x");
        notifier.done(404L, "SUCCESS");
        // 不抛异常即通过
    }
}