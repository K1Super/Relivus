package com.relivus.dialect;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;

/**
 * 不支持的数据库类型。
 */
public class UnsupportedDatabaseException extends RelivusException {

    public UnsupportedDatabaseException(String productName) {
        super(ErrorCode.UNSUPPORTED_DATABASE, "Unsupported database product: " + productName);
    }
}