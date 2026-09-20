package com.relivus.generator;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.ai.AiHttpClient;
import com.relivus.config.RelivusProperties;
import com.relivus.service.IAiConfigService;
import com.relivus.generator.ValueGeneratorFactory.ResolvedGenerator;
import com.relivus.schema.model.CheckConstraintMetadata;
import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.ForeignKeyMetadata;
import com.relivus.schema.model.TableMetadata;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 值生成器解析工厂测试（解析顺序：用户配置 > 自增 > 外键 > ENUM > CHECK > 主键 > 列名 > 类型）。
 */
class ValueGeneratorFactoryTest {

    private final ValueGeneratorFactory factory = new ValueGeneratorFactory();

    private static ColumnMetadata col(String name, String type, boolean nullable, boolean auto, List<String> enums) {
        return new ColumnMetadata(name, type, nullable, null, auto, enums);
    }

    private static TableMetadata table(ColumnMetadata... columns) {
        return new TableMetadata("t", List.of(columns), List.of(), List.of(), List.of(), null);
    }

    private static GenerationContext ctx(ColumnMetadata column, TableMetadata table, long row,
                                         Map<String, Object> params) {
        return new GenerationContext(table.tableName(), column, row, 10, params, null);
    }

    @Test
    void userConfiguredGeneratorWins() {
        ColumnMetadata column = col("name", "VARCHAR", true, false, List.of());
        ResolvedGenerator resolved = factory.resolve(column, table(column), "fixed", Map.of("value", "X"));
        Object value = resolved.generate(ctx(column, table(column), 0, Map.of()));
        assertThat(value).isEqualTo("X");
    }

    @Test
    void sequenceUserGenerator() {
        ColumnMetadata column = col("code", "VARCHAR", false, false, List.of());
        ResolvedGenerator resolved = factory.resolve(column, table(column), "sequence", null);
        Object first = resolved.generate(ctx(column, table(column), 0, Map.of()));
        Object second = resolved.generate(ctx(column, table(column), 1, Map.of()));
        assertThat(((Long) second)).isEqualTo(((Long) first) + 1);
    }

    @Test
    void unknownUserGeneratorThrows9002() {
        ColumnMetadata column = col("name", "VARCHAR", true, false, List.of());
        assertThatThrownBy(() -> factory.resolve(column, table(column), "no_such", null))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", com.relivus.common.exception.ErrorCode.VALIDATION_FAILED.getCode());
    }

    @Test
    void autoIncrementColumnSkipped() {
        ColumnMetadata column = col("id", "BIGINT", false, true, List.of());
        assertThat(factory.resolve(column, table(column), null, null)).isNull();
    }

    @Test
    void foreignKeyColumnUsesFkGenerator() {
        ColumnMetadata column = col("parent_id", "BIGINT", false, false, List.of());
        TableMetadata meta = new TableMetadata("child", List.of(column),
                List.of(new ForeignKeyMetadata("fk", "parent_id", "parent", "id", false)),
                List.of(), List.of(), "id");
        ResolvedGenerator resolved = factory.resolve(column, meta, null, null);
        assertThat(resolved.generator()).isInstanceOf(ForeignKeyGenerator.class);
        assertThatThrownBy(() -> resolved.generate(ctx(column, meta, 0, Map.of())))
                .isInstanceOf(IllegalStateException.class);
        Object value = resolved.generate(ctx(column, meta, 0, Map.of(ForeignKeyGenerator.FK_VALUE_PARAM, 42L)));
        assertThat(value).isEqualTo(42L);
    }

    @Test
    void enumColumnUsesEnumGenerator() {
        ColumnMetadata column = col("status", "VARCHAR", false, false, List.of("new", "done"));
        ResolvedGenerator resolved = factory.resolve(column, table(column), null, null);
        Object value = resolved.generate(ctx(column, table(column), 0, Map.of()));
        assertThat(value).isIn("new", "done");
    }

    @Test
    void checkInConstraintFeedsEnum() {
        ColumnMetadata column = col("state", "VARCHAR", false, false, List.of());
        TableMetadata meta = new TableMetadata("t", List.of(column), List.of(), List.of(),
                List.of(new CheckConstraintMetadata("chk", List.of("state"),
                        com.relivus.schema.model.CheckType.IN, List.of("red", "blue"), null, null)), null);
        ResolvedGenerator resolved = factory.resolve(column, meta, null, null);
        Object value = resolved.generate(ctx(column, meta, 0, Map.of()));
        assertThat(value).isIn("red", "blue");
    }

    @Test
    void checkRangeConstraintFeedsRandomInt() {
        ColumnMetadata column = col("age", "INT", false, false, List.of());
        TableMetadata meta = new TableMetadata("t", List.of(column), List.of(), List.of(),
                List.of(new CheckConstraintMetadata("chk", List.of("age"),
                        com.relivus.schema.model.CheckType.RANGE, List.of(),
                        new BigDecimal("18"), new BigDecimal("65"))), null);
        ResolvedGenerator resolved = factory.resolve(column, meta, null, null);
        Object value = resolved.generate(ctx(column, meta, 0, Map.of()));
        long v = (Long) value;
        assertThat(v).isBetween(18L, 65L);
    }

    @Test
    void nonAutoIncrementIntPrimaryKeyUsesSequence() {
        ColumnMetadata column = col("id", "INT", false, false, List.of());
        TableMetadata meta = new TableMetadata("t", List.of(column), List.of(), List.of(), List.of(), "id");
        ResolvedGenerator resolved = factory.resolve(column, meta, null, null);
        assertThat(resolved.generator()).isInstanceOf(SequenceGenerator.class);
        assertThat(resolved.generate(ctx(column, meta, 0, Map.of()))).isEqualTo(1L);
    }

    @Test
    void nameHeuristicEmailAndPhone() {
        ColumnMetadata emailCol = col("email", "VARCHAR", true, false, List.of());
        ResolvedGenerator emailGen = factory.resolve(emailCol, table(emailCol), null, null);
        Object email = emailGen.generate(ctx(emailCol, table(emailCol), 0, Map.of()));
        assertThat(email).isInstanceOf(String.class);
        assertThat(email.toString()).matches(".+@.+");

        ColumnMetadata phoneCol = col("phone", "VARCHAR", true, false, List.of());
        ResolvedGenerator phoneGen = factory.resolve(phoneCol, table(phoneCol), null, null);
        Object phone = phoneGen.generate(ctx(phoneCol, table(phoneCol), 0, Map.of()));
        assertThat(phone.toString()).matches("1[3-9]\\d{9}");
    }

    @Test
    void skippableTypeNullableReturnsNull_notNullThrows3001() {
        ColumnMetadata nullable = col("payload", "JSON", true, false, List.of());
        assertThat(factory.resolve(nullable, table(nullable), null, null)).isNull();

        ColumnMetadata notNull = col("payload", "JSON", false, false, List.of());
        assertThatThrownBy(() -> factory.resolve(notNull, table(notNull), null, null))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", com.relivus.common.exception.ErrorCode.GENERATION_FAILED.getCode());
    }

    @Test
    void typeDefaultIntColumn() {
        ColumnMetadata column = col("random_col", "INT", false, false, List.of());
        ResolvedGenerator resolved = factory.resolve(column, table(column), null, null);
        Object value = resolved.generate(ctx(column, table(column), 0, Map.of()));
        assertThat(value).isInstanceOf(Long.class);
    }

    @Test
    void skippableUnknownTypeNullableReturnsNull() {
        ColumnMetadata column = col("weird_col", "CUSTOM_TYPE", true, false, List.of());
        assertThat(factory.resolve(column, table(column), null, null)).isNull();
    }

    @Test
    void aiBranchRequiresInjectedComponents() {
        ColumnMetadata column = col("bio", "VARCHAR", true, false, List.of());
        assertThatThrownBy(() -> factory.resolve(column, table(column), "ai", Map.of("prompt", "x")))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED.getCode());
    }

    @Test
    void aiBranchResolvesAiGeneratorWithPrompt() {
        ValueGeneratorFactory injected = new ValueGeneratorFactory();
        injected.setAiSupport(mock(AiHttpClient.class), mock(IAiConfigService.class), new RelivusProperties());
        ColumnMetadata column = col("bio", "VARCHAR", true, false, List.of());

        ResolvedGenerator resolved = injected.resolve(column, table(column), "ai", Map.of("prompt", "写一句话"));

        assertThat(resolved.generator()).isInstanceOf(AiGenerator.class);
        assertThat(resolved.generator().name()).isEqualTo("ai");
    }

    @Test
    void aiBranchRequiresPrompt() {
        ValueGeneratorFactory injected = new ValueGeneratorFactory();
        injected.setAiSupport(mock(AiHttpClient.class), mock(IAiConfigService.class), new RelivusProperties());
        ColumnMetadata column = col("bio", "VARCHAR", true, false, List.of());

        assertThatThrownBy(() -> injected.resolve(column, table(column), "ai", null))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED.getCode());
    }
}