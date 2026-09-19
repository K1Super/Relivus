package com.relivus.controller;

import com.relivus.common.api.ApiResponse;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dto.TaskDataPage;
import com.relivus.dto.TaskGeneratedTable;
import com.relivus.dto.TaskResponse;
import com.relivus.entity.TaskEntity;
import com.relivus.task.TaskDataService;
import com.relivus.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统一任务接口（任务 API 统一为 /api/tasks/**）。
 *
 * <p>除任务生命周期外，还承载生成数据回看：{@code /{id}/tables}（表清单）与
 * {@code /{id}/data}（分页数据），供任务成功后前端直接可视化本次生成的行。
 */
@Tag(name = "任务管理")
@Validated
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskDataService taskDataService;

    public TaskController(TaskService taskService, TaskDataService taskDataService) {
        this.taskService = taskService;
        this.taskDataService = taskDataService;
    }

    @Operation(summary = "任务列表")
    @GetMapping
    public ApiResponse<List<TaskResponse>> list(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(taskService.listTasks(limit, offset).stream().map(TaskResponse::from).toList());
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(TaskResponse.from(taskService.getTask(id)));
    }

    @Operation(summary = "取消任务")
    @PostMapping("/{id}/cancel")
    public ApiResponse<Boolean> cancel(@PathVariable Long id) {
        return ApiResponse.ok(taskService.cancel(id));
    }

    @Operation(summary = "生成任务数据回看：表清单")
    @GetMapping("/{id}/tables")
    public ApiResponse<List<TaskGeneratedTable>> generatedTables(@PathVariable Long id) {
        TaskEntity task = taskService.getTask(id);
        return ApiResponse.ok(taskDataService.listTables(task));
    }

    @Operation(summary = "生成任务数据回看：分页数据")
    @GetMapping("/{id}/data")
    public ApiResponse<TaskDataPage> generatedData(
            @PathVariable Long id,
            @RequestParam String table,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        validatePagination(limit, offset);
        TaskEntity task = taskService.getTask(id);
        return ApiResponse.ok(taskDataService.readData(task, table, limit, offset));
    }

    private static void validatePagination(int limit, int offset) {
        if (limit < 1 || limit > TaskDataService.MAX_LIMIT) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                    "limit 必须在 1-" + TaskDataService.MAX_LIMIT + " 之间");
        }
        if (offset < 0) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED, "offset 必须 >= 0");
        }
    }
}
