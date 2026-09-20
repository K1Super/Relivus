package com.relivus.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relivus.common.api.ApiResponse;
import com.relivus.common.api.IdempotencyGuard;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dialect.DatabaseDialect;
import com.relivus.dto.GenerationConfig;
import com.relivus.dto.TaskResponse;
import com.relivus.generator.DataGenerationEngine;
import com.relivus.generator.GenerationRunResult;
import com.relivus.repository.AuditLogRepository;
import com.relivus.service.IConnectionService;
import com.relivus.task.ITaskDataService;
import com.relivus.task.ITaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据生成接口。
 *
 * <pre>
 * POST /api/generation/preview  同步小批量试生成（每表 ≤ 5 行，真实插入）
 * POST /api/generation/execute  异步任务执行（任务落 df_task，进度经 SSE 推送）
 * </pre>
 *
 * <p>预览为有副作用操作（向目标库真实写入少量行），前端需明确提示；execute 支持
 * Idempotency-Key 防重复提交。
 */
@Tag(name = "数据生成")
@RestController
@RequestMapping("/api/generation")
public class GenerationController {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationController.class);

    /** 预览时每表行数上限。 */
    private static final int PREVIEW_ROWS_PER_TABLE = 5;

    private final IConnectionService connectionService;
    private final DataGenerationEngine engine;
    private final ITaskService taskService;
    private final ITaskDataService taskDataService;
    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper objectMapper;
    private final AuditLogRepository auditLogRepository;

    public GenerationController(IConnectionService connectionService, DataGenerationEngine engine,
                                ITaskService taskService, ITaskDataService taskDataService,
                                IdempotencyGuard idempotencyGuard,
                                ObjectMapper objectMapper, AuditLogRepository auditLogRepository) {
        this.connectionService = connectionService;
        this.engine = engine;
        this.taskService = taskService;
        this.taskDataService = taskDataService;
        this.idempotencyGuard = idempotencyGuard;
        this.objectMapper = objectMapper;
        this.auditLogRepository = auditLogRepository;
    }

    @Operation(summary = "预览生成（小批量真实插入）")
    @PostMapping("/preview")
    public ApiResponse<GenerationRunResult> preview(@Valid @RequestBody GenerationConfig request) {
        DataSource dataSource = connectionService.resolveDataSource(request.connectionId());
        DatabaseDialect dialect = connectionService.dialect(request.connectionId());
        GenerationConfig previewConfig = shrinkConfig(request);
        GenerationRunResult result = engine.execute(dataSource, dialect, previewConfig, null);
        auditLogRepository.insert("generation_preview", request.connectionId().toString(),
                result.rowsByTable().toString(), "success", MDC.get("traceId"));
        return ApiResponse.ok(result);
    }

    @Operation(summary = "执行生成（异步任务）")
    @PostMapping("/execute")
    public ApiResponse<TaskResponse> execute(
            @Valid @RequestBody GenerationConfig request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long taskId = idempotencyGuard.getOrCreate(idempotencyKey, () -> {
            Long id = createGenerationTask(request);
            auditLogRepository.insert("generation_execute", request.connectionId().toString(),
                    "taskId=" + id, "success", MDC.get("traceId"));
            return id;
        });
        return ApiResponse.ok(taskResponse(taskId));
    }

    private Long createGenerationTask(GenerationConfig request) {
        final String configJson;
        try {
            configJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED, "配置序列化失败：" + e.getMessage(), e);
        }
        Long taskId = taskService.createTask("GENERATION", request.connectionId(), configJson);
        taskService.executeAsync(taskId, ctx -> {
            DataSource dataSource = connectionService.resolveDataSource(request.connectionId());
            DatabaseDialect dialect = connectionService.dialect(request.connectionId());
            // 数据回看基线：必须在引擎插入前采集，失败则任务整体失败，保证回看准确性
            Map<String, Long> baselines = taskDataService.captureBaselines(dataSource, dialect, request);
            try {
                taskService.updateDataBaseline(taskId, objectMapper.writeValueAsString(baselines));
            } catch (JsonProcessingException e) {
                throw new RelivusException(ErrorCode.VALIDATION_FAILED, "数据回看基线序列化失败：" + e.getMessage(), e);
            }
            engine.execute(dataSource, dialect, request, ctx.generationListener());
        });
        return taskId;
    }

    /** 预览配置：行数收敛到小批量，且强制不清空目标表。 */
    private static GenerationConfig shrinkConfig(GenerationConfig request) {
        List<GenerationConfig.TableConfig> tables = new ArrayList<>();
        for (GenerationConfig.TableConfig table : request.tables()) {
            int rows = Math.min(table.rowCount(), PREVIEW_ROWS_PER_TABLE);
            tables.add(new GenerationConfig.TableConfig(table.table(), rows, table.columns()));
        }
        return new GenerationConfig(request.connectionId(), tables, request.samplingStrategy(),
                false, request.batchSize());
    }

    private TaskResponse taskResponse(Long taskId) {
        return TaskResponse.from(taskService.getTask(taskId));
    }
}