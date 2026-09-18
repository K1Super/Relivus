package com.relivus.task;

import com.relivus.entity.TaskEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSE 进度端点测试（DOC-05 / DOC-11.8）。
 *
 * <p>验证前置任务校验、返回 text/event-stream 与订阅注册。
 */
class SseProgressControllerTest {

    @Test
    void progressRegistersEmitterAndReturnsEventStream() throws Exception {
        TaskService taskService = mock(TaskService.class);
        SseTaskProgressNotifier notifier = mock(SseTaskProgressNotifier.class);
        TaskEntity task = new TaskEntity();
        task.setId(5L);
        task.setTaskType("generation");
        when(taskService.getTask(5L)).thenReturn(task);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SseProgressController(taskService, notifier))
                .build();

        mockMvc.perform(get("/api/tasks/5/progress").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(taskService).getTask(5L);
        verify(notifier).register(eq(5L), any(SseEmitter.class));
        verify(notifier).log(eq(5L), eq("INFO"), eq("SSE 连接已建立"));
    }
}