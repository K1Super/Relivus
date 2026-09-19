package com.relivus.generator;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;

/**
 * 唯一约束冲突超限。
 *
 * <p>本地唯一检测与数据库唯一索引兜底均达上限时抛出。
 */
public class UniqueConstraintException extends RelivusException {

    public UniqueConstraintException(String message) {
        super(ErrorCode.UNIQUE_CONSTRAINT_EXCEEDED, message);
    }

    public UniqueConstraintException(String message, Throwable cause) {
        super(ErrorCode.UNIQUE_CONSTRAINT_EXCEEDED, message, cause);
    }
}