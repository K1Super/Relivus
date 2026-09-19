package com.relivus.common.exception;

import com.relivus.common.api.ApiResponse;
import com.relivus.common.api.FieldErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * 全局异常处理器。
 *
 * <p>职责：
 * <ul>
 *   <li>业务异常按 {@link ErrorCode} 元数据映射 HTTP 状态并返回统一结构（含 traceId）；</li>
 *   <li>校验类异常返回 {@code VALIDATION_FAILED} + 字段级明细 {@code fieldErrors}；</li>
 *   <li>日志级别按 {@code Severity} 选择：WARN=可预期业务失败，ERROR=系统故障（完整堆栈）；</li>
 *   <li>服务端故障（5xxxxx）对外仅返回通用提示，细节只落服务端日志，防止内部信息泄露。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 服务端故障对外通用提示（不泄露内部细节，可凭 traceId 检索日志）。 */
    private static final String INTERNAL_PUBLIC_MESSAGE = "Internal server error, detail in server log";

    /** 业务异常。 */
    @ExceptionHandler(RelivusException.class)
    public ResponseEntity<ApiResponse<Void>> handleRelivus(RelivusException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        if (errorCode.getSeverity() == ErrorCode.Severity.ERROR) {
            log.error("Business failure code={}, traceId={}", errorCode.getCode(),
                    currentTraceId(), ex);
        } else {
            log.warn("Business exception code={}, message={}", errorCode.getCode(), ex.getMessage());
        }
        String message = errorCode.isServerFault() ? INTERNAL_PUBLIC_MESSAGE : ex.getMessage();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode, message));
    }

    /** @Valid 请求体校验失败（汇聚全部字段错误）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        return validationResponse(ex.getBindingResult().getFieldErrors());
    }

    /** 表单绑定校验失败。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        return validationResponse(ex.getBindingResult().getFieldErrors());
    }

    /** 参数缺失 / 类型不匹配 / 请求体不可读。 */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code, "Request parameter invalid"));
    }

    /** 兜底：系统异常（完整日志，对外通用提示）。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        log.error("Unhandled system exception, traceId={}", currentTraceId(), ex);
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code, INTERNAL_PUBLIC_MESSAGE));
    }

    /** 汇聚字段错误：message 取首条，fieldErrors 全量返回。 */
    private static ResponseEntity<ApiResponse<Void>> validationResponse(List<FieldError> errors) {
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        List<FieldErrorDetail> details = errors.stream()
                .map(e -> new FieldErrorDetail(e.getField(),
                        e.getDefaultMessage() == null ? "Invalid value" : e.getDefaultMessage()))
                .toList();
        String first = details.isEmpty() ? "Parameter validation failed" : details.get(0).message();
        log.warn("Validation failed: {}", details);
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code, first, details));
    }

    private static String currentTraceId() {
        return org.slf4j.MDC.get(ApiResponse.TRACE_ID_MDC_KEY);
    }
}