package com.relivus.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relivus.common.api.ApiResponse;
import com.relivus.common.api.IdempotencyGuard;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dialect.DatabaseDialect;
import com.relivus.dto.JoinVerificationRequest;
import com.relivus.dto.MaskingPreviewResponse;
import com.relivus.dto.MaskingTaskRequest;
import com.relivus.dto.MaskingTaskRequest.VerifyTableSpec;
import com.relivus.dto.TaskResponse;
import com.relivus.masking.JoinConsistencyVerifier;
import com.relivus.masking.JoinConsistencyVerifier.JoinVerificationResult;
import com.relivus.masking.MaskingEngine;
import com.relivus.repository.AuditLogRepository;
import com.relivus.service.ConnectionService;
import com.relivus.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.Locale;

/**
 * 数据脱敏接口。
 *
 * <pre>
 * POST /api/masking/preview  同步预览（不落库不写映射）
 * POST /api/masking/execute  异步任务执行（脱敏前按 verifyTables 建 JOIN 快照）
 * POST /api/masking/verify   比对 JOIN 一致性（消费 execute 阶段生成的快照，比对后清理）
 * </pre>
 *
 * <p>快照表命名 {@code df_snap_<table>}（表名经白名单校验）；verify 为一次性消费，
 * 重复校验需重新执行含 verifyTables 的脱敏任务。
 */
@Tag(name = "数据脱敏")
@RestController
@RequestMapping("/api/masking")
public class MaskingController {

    private static final Logger LOG = LoggerFactory.getLogger(MaskingController.class);

    private static final String SNAPSHOT_PREFIX = "df_snap_";
    /** 表名白名单：仅字母/数字/下划线，杜绝 SQL 注入路径。 */
    private static final java.util.regex.Pattern IDENTIFIER_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z0-9_]+");

    private final ConnectionService connectionService;
    private final MaskingEngine engine;
    private final TaskService taskService;
    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper objectMapper;
    private final AuditLogRepository auditLogRepository;

    public MaskingController(ConnectionService connectionService, MaskingEngine engine,
                             TaskService taskService, IdempotencyGuard idempotencyGuard,
                             ObjectMapper objectMapper, AuditLogRepository auditLogRepository) {
        this.connectionService = connectionService;
        this.engine = engine;
        this.taskService = taskService;
        this.idempotencyGuard = idempotencyGuard;
        this.objectMapper = objectMapper;
        this.auditLogRepository = auditLogRepository;
    }

    @Operation(summary = "预览脱敏（不落库）")
    @PostMapping("/preview")
    public ApiResponse<MaskingPreviewResponse> preview(@Valid @RequestBody MaskingTaskRequest request) {
        DataSource dataSource = connectionService.resolveDataSource(request.connectionId());
        DatabaseDialect dialect = connectionService.dialect(request.connectionId());
        MaskingPreviewResponse response = engine.preview(dataSource, dialect, request, 20);
        auditLogRepository.insert("masking_preview", request.connectionId().toString(),
                "rows=" + response.rows().size(), "success", MDC.get("traceId"));
        return ApiResponse.ok(response);
    }

    @Operation(summary = "执行脱敏（异步任务）")
    @PostMapping("/execute")
    public ApiResponse<TaskResponse> execute(
            @Valid @RequestBody MaskingTaskRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        validateVerifyTables(request);
        Long taskId = idempotencyGuard.getOrCreate(idempotencyKey, () -> {
            Long id = createMaskingTask(request);
            auditLogRepository.insert("masking_execute", request.connectionId().toString(),
                    "taskId=" + id, "success", MDC.get("traceId"));
            return id;
        });
        return ApiResponse.ok(TaskResponse.from(taskService.getTask(taskId)));
    }

    @Operation(summary = "验证 JOIN 一致性（消费脱敏前快照）")
    @PostMapping("/verify")
    public ApiResponse<JoinVerificationResult> verify(@Valid @RequestBody JoinVerificationRequest request) {
        DataSource dataSource = connectionService.resolveDataSource(request.connectionId());
        DatabaseDialect dialect = connectionService.dialect(request.connectionId());
        JdbcTemplate jt = new JdbcTemplate(dataSource);
        String snapshot = snapshotName(request.targetTable());
        requireSnapshot(jt, dialect, snapshot, request.targetTable());
        JoinConsistencyVerifier verifier = new JoinConsistencyVerifier();
        JoinVerificationResult result = verifier.verify(jt, dialect, snapshot,
                request.joinSql(), request.pkColumn(), request.joinKeyColumn());
        auditLogRepository.insert("masking_verify", request.connectionId().toString(),
                result.message(), "success", MDC.get("traceId"));
        return ApiResponse.ok(result);
    }

    private Long createMaskingTask(MaskingTaskRequest request) {
        final String configJson;
        try {
            configJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED, "配置序列化失败：" + e.getMessage(), e);
        }
        Long taskId = taskService.createTask("MASKING", request.connectionId(), configJson);
        taskService.executeAsync(taskId, ctx -> {
            DataSource dataSource = connectionService.resolveDataSource(request.connectionId());
            DatabaseDialect dialect = connectionService.dialect(request.connectionId());
            JdbcTemplate jt = new JdbcTemplate(dataSource);
            // 脱敏前采样快照
            if (request.verifyTables() != null) {
                JoinConsistencyVerifier verifier = new JoinConsistencyVerifier();
                for (VerifyTableSpec spec : request.verifyTables()) {
                    verifier.createSnapshot(jt, dialect, snapshotName(spec.table()),
                            spec.table(), spec.pkColumn(), spec.joinKeyColumn(), spec.whereClause());
                    ctx.log("INFO", "JOIN 快照已创建：" + spec.table());
                }
            }
            engine.execute(dataSource, dialect, request, ctx.maskingListener());
        });
        return taskId;
    }

    private void validateVerifyTables(MaskingTaskRequest request) {
        if (request.verifyTables() == null) {
            return;
        }
        for (VerifyTableSpec spec : request.verifyTables()) {
            if (!IDENTIFIER_PATTERN.matcher(spec.table()).matches()
                    || !IDENTIFIER_PATTERN.matcher(spec.pkColumn()).matches()
                    || !IDENTIFIER_PATTERN.matcher(spec.joinKeyColumn()).matches()) {
                throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                        "verifyTables 表名/列名仅允许字母数字下划线");
            }
            if (!IDENTIFIER_PATTERN.matcher(snapshotName(spec.table())).matches()) {
                throw new RelivusException(ErrorCode.VALIDATION_FAILED, "快照表名非法");
            }
        }
    }

    private void requireSnapshot(JdbcTemplate jt, DatabaseDialect dialect, String snapshot, String targetTable) {
        try {
            jt.queryForObject("SELECT COUNT(*) FROM " + dialect.quoteIdentifier(snapshot), Long.class);
        } catch (RuntimeException e) {
            throw new RelivusException(ErrorCode.JOIN_VERIFICATION_FAILED,
                    "快照 " + snapshot + " 不存在：请先在脱敏任务配置 verifyTables 并完成执行", e);
        }
    }

    private static String snapshotName(String table) {
        return SNAPSHOT_PREFIX + table.toLowerCase(Locale.ROOT);
    }
}