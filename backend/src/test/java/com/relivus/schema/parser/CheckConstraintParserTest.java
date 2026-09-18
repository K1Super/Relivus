package com.relivus.schema.parser;

import com.relivus.schema.model.CheckConstraintMetadata;
import com.relivus.schema.model.CheckType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CHECK 约束解析器测试（DOC-08：IN、范围、UNKNOWN）。
 */
class CheckConstraintParserTest {

    private static final Set<String> COLUMNS = Set.of("status", "age", "level", "id");

    @Test
    void parsesInList() {
        CheckConstraintMetadata meta = CheckConstraintParser.parse(
                "chk_status", "status IN ('active','deleted')", COLUMNS);
        assertThat(meta.type()).isEqualTo(CheckType.IN);
        assertThat(meta.columns()).containsExactly("status");
        assertThat(meta.allowedValues()).containsExactly("active", "deleted");
    }

    @Test
    void parsesParenthesizedAndQuotedIdentifiers() {
        CheckConstraintMetadata meta = CheckConstraintParser.parse(
                "chk_status", "(`status` in ('a','b'))", COLUMNS);
        assertThat(meta.type()).isEqualTo(CheckType.IN);
        assertThat(meta.allowedValues()).containsExactly("a", "b");
    }

    @Test
    void parsesPostgresAnyArrayForm() {
        // PG 将 IN 改写为 = ANY(ARRAY[...])，含类型转换
        CheckConstraintMetadata meta = CheckConstraintParser.parse(
                "chk_status", "status = ANY (ARRAY['a'::text, 'b'::text])", COLUMNS);
        assertThat(meta.type()).isEqualTo(CheckType.IN);
        assertThat(meta.allowedValues()).containsExactly("a", "b");
    }

    @Test
    void parsesRange() {
        CheckConstraintMetadata meta = CheckConstraintParser.parse(
                "chk_age", "age >= 18 AND age <= 65", COLUMNS);
        assertThat(meta.type()).isEqualTo(CheckType.RANGE);
        assertThat(meta.minValue()).isEqualByComparingTo("18");
        assertThat(meta.maxValue()).isEqualByComparingTo("65");
    }

    @Test
    void multiColumnExpressionIsUnknown() {
        CheckConstraintMetadata meta = CheckConstraintParser.parse(
                "chk_multi", "age > 0 AND status <> 'x'", COLUMNS);
        assertThat(meta.type()).isEqualTo(CheckType.UNKNOWN);
    }

    @Test
    void unknownColumnInExpressionIsNotInferred() {
        CheckConstraintMetadata meta = CheckConstraintParser.parse(
                "chk_other", "other_col IN ('a','b')", COLUMNS);
        assertThat(meta.type()).isEqualTo(CheckType.UNKNOWN);
    }

    @Test
    void unrecognizedExprIsUnknown() {
        assertThat(CheckConstraintParser.parse("chk", "status != 'x'", COLUMNS).type())
                .isEqualTo(CheckType.UNKNOWN);
        assertThat(CheckConstraintParser.parse("chk", "LENGTH(name) > 2", COLUMNS).type())
                .isEqualTo(CheckType.UNKNOWN);
        assertThat(CheckConstraintParser.parse("chk", "status > 1", COLUMNS).type())
                .isEqualTo(CheckType.UNKNOWN);
    }

    @Test
    void nullOrBlankDefinitionIsUnknown() {
        assertThat(CheckConstraintParser.parse("chk", null, COLUMNS).type())
                .isEqualTo(CheckType.UNKNOWN);
        assertThat(CheckConstraintParser.parse("chk", "   ", COLUMNS).type())
                .isEqualTo(CheckType.UNKNOWN);
    }

    @Test
    void singleSidedBoundIsUnknown() {
        assertThat(CheckConstraintParser.parse("chk", "age <= 65", COLUMNS).type())
                .isEqualTo(CheckType.UNKNOWN);
    }
}