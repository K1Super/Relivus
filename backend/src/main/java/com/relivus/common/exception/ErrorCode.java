package com.relivus.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 全局错误码表。
 *
 * <p><b>编码结构（六位三段式 {@code A-BB-CCC}）</b>：
 * <pre>
 *   A   责任方：1=调用方可修正（HTTP 4xx 或 502 上游不可达），5=服务端故障（HTTP 5xx）
 *   BB  模块域：00=通用、10=连接、20=Schema 内省、30=数据生成、40=数据脱敏、50=任务
 *   CCC 模块内错误序号（001-999）
 * </pre>
 *
 * <p><b>元数据</b>：每个码绑定 {@link HttpStatus} 与严重级别 {@link Severity}，
 * 由 {@code GlobalExceptionHandler} 统一用于 HTTP 状态映射与日志级别选择，
 * 保证「码值稳定为契约、行为随元数据自解释」。
 *
 * <p><b>治理规则</b>：
 * <ul>
 *   <li>新增码必须复用 {@code A-BB} 段并以 001 起递增，禁止跳号复用已废弃码值。</li>
 *   <li>码值一经发布即冻结；语义变化须新增码并保留旧码一段过渡期。</li>
 *   <li>前端本地合成码使用 99xxxx 段（如网络层兜底 999000），后端不得占用。</li>
 * </ul>
 *
 * @see RelivusException
 * @see com.relivus.common.exception.GlobalExceptionHandler
 */
public enum ErrorCode {

    // ==================== 模块 00：通用 ====================
    /** 参数校验失败（含字段级明细 {@code fieldErrors}）。 */
    VALIDATION_FAILED(100002, HttpStatus.BAD_REQUEST, Severity.WARN),
    /** 鉴权失败：Token 缺失、错误或已失效。 */
    AUTH_FAILED(100003, HttpStatus.UNAUTHORIZED, Severity.WARN),
    /** 目标资源不存在或已被清理。 */
    RESOURCE_NOT_FOUND(100004, HttpStatus.NOT_FOUND, Severity.WARN),
    /** 资源状态冲突，当前状态不允许该操作。 */
    RESOURCE_CONFLICT(100005, HttpStatus.CONFLICT, Severity.WARN),

    /** 系统内部错误（兜底，详情仅落服务端日志）。 */
    INTERNAL_ERROR(500001, HttpStatus.INTERNAL_SERVER_ERROR, Severity.ERROR),
    /** 密钥加解密失败（服务端配置/密钥问题）。 */
    CRYPTO_FAILED(500002, HttpStatus.INTERNAL_SERVER_ERROR, Severity.ERROR),

    // ==================== 模块 10：连接 ====================
    /** 目标库连接失败（配置错误或目标库不可达）。 */
    CONNECTION_FAILED(110001, HttpStatus.BAD_GATEWAY, Severity.WARN),
    /** 不支持的数据库类型。 */
    UNSUPPORTED_DATABASE(110002, HttpStatus.BAD_REQUEST, Severity.WARN),

    // ==================== 模块 20：Schema 内省 ====================
    /** Schema 内省失败（元数据读取失败/权限不足）。 */
    SCHEMA_INTROSPECTION_FAILED(120001, HttpStatus.BAD_GATEWAY, Severity.WARN),

    // ==================== 模块 30：数据生成 ====================
    /** 唯一约束冲突超限，无法生成满足约束的数据。 */
    UNIQUE_CONSTRAINT_EXCEEDED(130002, HttpStatus.CONFLICT, Severity.WARN),
    /** 循环依赖外键列为 NOT NULL，需先建立初始行。 */
    CIRCULAR_FK_NOT_NULL(130003, HttpStatus.CONFLICT, Severity.WARN),
    /** 数据生成失败（引擎/数据源系统故障）。 */
    GENERATION_FAILED(530001, HttpStatus.INTERNAL_SERVER_ERROR, Severity.ERROR),

    // ==================== 模块 40：数据脱敏 ====================
    /** JOIN 一致性验证失败（跨表脱敏结果不一致）。 */
    JOIN_VERIFICATION_FAILED(140002, HttpStatus.CONFLICT, Severity.WARN),
    /** 数据脱敏失败（引擎系统故障）。 */
    MASKING_FAILED(540001, HttpStatus.INTERNAL_SERVER_ERROR, Severity.ERROR),

    // ==================== 模块 50：任务 ====================
    /** 任务不存在或已被清理。 */
    TASK_NOT_FOUND(150001, HttpStatus.NOT_FOUND, Severity.WARN),
    /** 任务状态不允许取消（仅运行中可取消）。 */
    TASK_CANCEL_FAILED(150002, HttpStatus.CONFLICT, Severity.WARN),
    /** 实时进度（SSE）连接数超限。 */
    SSE_LIMIT_EXCEEDED(150003, HttpStatus.TOO_MANY_REQUESTS, Severity.WARN),
    /** 任务队列已满。 */
    TASK_QUEUE_FULL(150004, HttpStatus.TOO_MANY_REQUESTS, Severity.WARN),
    /** 任务未成功完成，生成数据不可回看（仅 SUCCESS 任务提供数据查看）。 */
    TASK_DATA_UNAVAILABLE(150005, HttpStatus.CONFLICT, Severity.WARN),

    // ==================== 模块 60：AI ====================
    /** AI 配置不存在或已被删除。 */
    AI_CONFIG_NOT_FOUND(160001, HttpStatus.NOT_FOUND, Severity.WARN),
    /** 上游 AI 服务调用失败（配置错误/网络不可达/上游 4xx-5xx）。 */
    AI_UPSTREAM_FAILED(160002, HttpStatus.BAD_GATEWAY, Severity.WARN),
    /** AI 服务内部故障（系统异常）。 */
    AI_SERVICE_ERROR(560001, HttpStatus.INTERNAL_SERVER_ERROR, Severity.ERROR);

    /** 六位错误码。 */
    private final int code;
    /** 绑定的 HTTP 状态（由全局异常处理器映射）。 */
    private final HttpStatus httpStatus;
    /** 严重级别：决定日志级别与监控告警策略。 */
    private final Severity severity;

    ErrorCode(int code, HttpStatus httpStatus, Severity severity) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.severity = severity;
    }

    /** 六位错误码。 */
    public int getCode() {
        return code;
    }

    /** 绑定的 HTTP 状态。 */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /** 严重级别。 */
    public Severity getSeverity() {
        return severity;
    }

    /** 是否为服务端故障（5xxxxx 段）：对外仅返回通用提示，不暴露内部细节。 */
    public boolean isServerFault() {
        return code >= 500000;
    }

    /**
     * 严重级别：WARN=可预期的业务失败（调用方责任），ERROR=系统故障（需告警与排查）。
     */
    public enum Severity {
        WARN,
        ERROR
    }
}