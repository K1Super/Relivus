package com.relivus.common.exception;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 错误码契约测试：码值唯一、结构合法、责任方与 HTTP/级别元数据一致（错误码治理规则）。 */
class ErrorCodeContractTest {

    @Test
    void codeValuesAreUniqueSixDigit() {
        Set<Integer> seen = new HashSet<>();
        for (ErrorCode ec : ErrorCode.values()) {
            assertThat(ec.getCode()).isBetween(100000, 599999);
            assertThat(seen.add(ec.getCode()))
                    .as("错误码重复: %d (%s)", ec.getCode(), ec.name())
                    .isTrue();
        }
    }

    @Test
    void responsibilityDigitMatchesHttpStatus() {
        for (ErrorCode ec : ErrorCode.values()) {
            int responsibility = ec.getCode() / 100000;
            assertThat(responsibility).as("%s 首位非法", ec.name()).isIn(1, 5);
            if (responsibility == 1) {
                assertThat(ec.getHttpStatus().is4xxClientError()
                        || ec.getHttpStatus().value() == 502)
                        .as("%s 调用方责任码必须映射 4xx 或 502", ec.name())
                        .isTrue();
            } else {
                assertThat(ec.getHttpStatus().is5xxServerError())
                        .as("%s 服务端责任码必须映射 5xx", ec.name())
                        .isTrue();
            }
        }
    }

    @Test
    void moduleSegmentIsRegistered() {
        for (ErrorCode ec : ErrorCode.values()) {
            int module = (ec.getCode() / 1000) % 100;
            assertThat(module).as("%s 模块段未注册", ec.name()).isIn(0, 10, 20, 30, 40, 50, 60);
        }
    }

    @Test
    void serverFaultSeverityMustBeError() {
        for (ErrorCode ec : ErrorCode.values()) {
            if (ec.getCode() >= 500000) {
                assertThat(ec.getSeverity()).as("%s 应为 ERROR", ec.name())
                        .isEqualTo(ErrorCode.Severity.ERROR);
            }
        }
    }
}