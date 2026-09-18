package com.relivus.schema.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 表/列元数据模型测试。
 */
class TableMetadataTest {

    private static final List<ColumnMetadata> COLUMNS = List.of(
            new ColumnMetadata("id", "BIGINT", false, null, true, List.of()),
            new ColumnMetadata("status", "VARCHAR", false, null, false, List.of("new", "done")),
            new ColumnMetadata("name", "VARCHAR", true, null, false, List.of()));

    private final TableMetadata table =
            new TableMetadata("orders", COLUMNS, List.of(), List.of(), List.of(), "id");

    @Test
    void findsColumnByName() {
        assertThat(table.column("status")).isEqualTo(COLUMNS.get(1));
        assertThat(table.column("missing")).isNull();
    }

    @Test
    void primaryKeyColumnPresentOrNull() {
        assertThat(table.primaryKeyColumn()).isEqualTo(COLUMNS.get(0));
        TableMetadata noPk = new TableMetadata("t", COLUMNS, List.of(), List.of(), List.of(), null);
        assertThat(noPk.primaryKeyColumn()).isNull();
    }

    @Test
    void enumColumnDetection() {
        assertThat(COLUMNS.get(1).isEnum()).isTrue();
        assertThat(COLUMNS.get(0).isEnum()).isFalse();
        assertThat(new ColumnMetadata("e", "ENUM", true, null, false, null).isEnum()).isFalse();
    }

    @Test
    void unknownCheckConstraintFactory() {
        CheckConstraintMetadata meta = CheckConstraintMetadata.unknown("chk", "def");
        assertThat(meta.type()).isEqualTo(CheckType.UNKNOWN);
        assertThat(meta.columns()).isEmpty();
    }
}