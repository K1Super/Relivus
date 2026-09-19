package com.relivus.dialect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类型映射判断测试。
 */
class DataTypeMappingTest {

    private final DataTypeMapping mapping = new MySQLDialect().getTypeMapping();

    @Test
    void exposesDdlTypes() {
        assertThat(mapping.booleanType()).isEqualTo("TINYINT(1)");
        assertThat(mapping.timestampType()).isEqualTo("DATETIME");
        assertThat(mapping.binaryType()).isEqualTo("BLOB");
        assertThat(mapping.autoIncrementClause()).isEqualTo("AUTO_INCREMENT");
        assertThat(mapping.enumDdl()).isEqualTo("ENUM('A','B')");
    }

    @Test
    void classifiesTypes() {
        assertThat(mapping.isBoolean("TINYINT(1)")).isTrue();
        assertThat(mapping.isBoolean("BOOLEAN")).isTrue();
        assertThat(mapping.isBoolean("INT")).isFalse();

        assertThat(mapping.isTemporal("DATETIME")).isTrue();
        assertThat(mapping.isTemporal("TIMESTAMP")).isTrue();
        assertThat(mapping.isTemporal("DATE")).isTrue();
        assertThat(mapping.isTemporal("VARCHAR")).isFalse();

        assertThat(mapping.isNumeric("INT")).isTrue();
        assertThat(mapping.isNumeric("BIGINT")).isTrue();
        assertThat(mapping.isNumeric("DECIMAL(10,2)")).isTrue();
        assertThat(mapping.isNumeric("FLOAT")).isTrue();
        assertThat(mapping.isNumeric("VARCHAR")).isFalse();
    }
}