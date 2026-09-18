package com.relivus.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Relivus 统一业务异常基类。
 *
 * <p>所有业务异常必须继承本类并携带 {@link ErrorCode}（含 HTTP 状态与严重级别元数据），
 * 由全局异常处理器统一转换为 {@code ApiResponse} 并映射 HTTP 状态，
 * 避免 Controller 层手写 try-catch。
 */
public class RelivusException extends RuntimeException {

    private final ErrorCode errorCode;

    public RelivusException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RelivusException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** 错误码枚举（含 HTTP 状态、严重级别元数据）。 */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** 六位错误码数值。 */
    public int getCode() {
        return errorCode.getCode();
    }

    /** 绑定的 HTTP 状态（全局异常处理器映射依据）。 */
    public HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}