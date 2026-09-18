package com.relivus.common.api;

import com.relivus.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.MDC;

import java.util.List;

/**
 * 统一接口响应结构（DOC-06 契约 v2）。
 *
 * <p>错误响应保证携带 {@code traceId}（源自 MDC，由 {@code TraceIdFilter} 写入），
 * 校验类错误附带 {@code fieldErrors} 明细，前端可精确提示到字段。
 *
 * @param code        错误码（六位三段式），0 表示成功；非 0 见 {@link ErrorCode}
 * @param message     提示信息
 * @param data        业务数据
 * @param traceId     链路追踪 ID（请求级唯一，贯穿服务端日志）
 * @param fieldErrors 字段级错误明细（仅校验类错误非空）
 * @param <T>         业务数据类型
 */
@Schema(description = "统一响应结构")
public record ApiResponse<T>(
        @Schema(description = "错误码，0=成功") int code,
        @Schema(description = "提示信息") String message,
        @Schema(description = "业务数据") T data,
        @Schema(description = "链路追踪 ID") String traceId,
        @Schema(description = "字段级错误明细") List<FieldErrorDetail> fieldErrors) {

    public static final int SUCCESS_CODE = 0;
    public static final String SUCCESS_MESSAGE = "success";

    /** TraceId 的 MDC key，由 {@code TraceIdFilter} 写入、本类读取，必须保持一致。 */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data, currentTraceId(), null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, null, currentTraceId(), null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, currentTraceId(), null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message,
                                           List<FieldErrorDetail> fieldErrors) {
        return new ApiResponse<>(errorCode.getCode(), message, null, currentTraceId(), fieldErrors);
    }

    /** 从 MDC 取当前请求 TraceId；无（如过滤器链之外）返回 null。 */
    private static String currentTraceId() {
        return MDC.get(TRACE_ID_MDC_KEY);
    }
}