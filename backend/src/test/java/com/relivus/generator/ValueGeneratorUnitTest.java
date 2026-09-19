package com.relivus.generator;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.schema.model.ColumnMetadata;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 各值生成器单元测试。
 */
class ValueGeneratorUnitTest {

    private static ColumnMetadata col(String name, String type, boolean nullable, List<String> enums) {
        return new ColumnMetadata(name, type, nullable, null, false, enums);
    }

    private static GenerationContext ctx(ColumnMetadata column, Map<String, Object> params, long row) {
        return new GenerationContext("t", column, row, 10, params, null);
    }

    @Test
    void randomIntRespectsRangeAndErrorsOnReversedBounds() {
        RandomIntGenerator generator = new RandomIntGenerator();
        ColumnMetadata column = col("n", "INT", false, List.of());
        Object value = generator.generate(ctx(column, Map.of("min", 5L, "max", 5L), 0));
        assertThat(value).isEqualTo(5L);
        assertThatThrownBy(() -> generator.generate(ctx(column, Map.of("min", 10L, "max", 1L), 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(generator.supports(column)).isTrue();
        assertThat(generator.supports(col("d", "DECIMAL", true, List.of()))).isFalse();
    }

    @Test
    void randomDecimalAppliesScaleAndErrors() {
        RandomDecimalGenerator generator = new RandomDecimalGenerator();
        ColumnMetadata column = col("amount", "DECIMAL", false, List.of());
        Object value = generator.generate(ctx(column, Map.of("min", 1.0, "max", 2.0, "scale", 2), 0));
        assertThat(value).isInstanceOf(BigDecimal.class);
        BigDecimal v = (BigDecimal) value;
        assertThat(v.scale()).isLessThanOrEqualTo(2);
        assertThat(v.doubleValue()).isBetween(1.0, 1.999);
        assertThatThrownBy(() -> generator.generate(ctx(column, Map.of("min", 5.0, "max", 5.0), 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(generator.supports(col("n", "INT", true, List.of()))).isFalse();
    }

    @Test
    void enumGeneratorModesAndError() {
        EnumGenerator generator = new EnumGenerator();
        ColumnMetadata column = col("status", "VARCHAR", false, List.of("a", "b"));
        Object first = generator.generate(ctx(column, Map.of("mode", "round_robin"), 0));
        Object second = generator.generate(ctx(column, Map.of("mode", "round_robin"), 1));
        assertThat(first).isEqualTo("a");
        assertThat(second).isEqualTo("b");

        Object random = generator.generate(ctx(column, Map.of(), 0));
        assertThat(random).isIn("a", "b");

        ColumnMetadata noValues = col("x", "VARCHAR", true, List.of());
        assertThatThrownBy(() -> generator.generate(ctx(noValues, Map.of(), 0)))
                .isInstanceOf(IllegalArgumentException.class);

        // 参数覆盖
        Object overridden = generator.generate(ctx(column, Map.of("values", List.of("z")), 0));
        assertThat(overridden).isEqualTo("z");
        assertThat(generator.supports(column)).isTrue();
    }

    @Test
    void fixedValueRequiresValueParam() {
        FixedValueGenerator generator = new FixedValueGenerator();
        ColumnMetadata column = col("c", "VARCHAR", true, List.of());
        assertThatThrownBy(() -> generator.generate(ctx(column, Map.of(), 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(generator.generate(ctx(column, Map.of("value", "ok"), 0))).isEqualTo("ok");
        assertThat(generator.supports(column)).isFalse();
    }

    @Test
    void regexGeneratorFallsBackOnInvalidPattern() {
        RegexGenerator generator = new RegexGenerator();
        ColumnMetadata column = col("phone", "VARCHAR", true, List.of());
        // 参数缺失改走带错误码的校验异常（不再抛裸 IllegalArgumentException）
        assertThatThrownBy(() -> generator.generate(ctx(column, Map.of(), 0)))
                .isInstanceOf(RelivusException.class)
                .extracting(e -> ((RelivusException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        String valid = (String) generator.generate(ctx(column, Map.of("pattern", "1[3-9]\\d{9}"), 0));
        assertThat(valid).matches("1[3-9]\\d{9}");
        String fallback = (String) generator.generate(ctx(column, Map.of("pattern", "[invalid"), 0));
        assertThat(fallback).isNotBlank();
        assertThat(generator.supports(column)).isFalse();
    }

    @Test
    void timestampGeneratorReturnsTypedTimestampInRange() {
        TimestampGenerator generator = new TimestampGenerator();
        ColumnMetadata column = col("created_at", "TIMESTAMP", false, List.of());
        long now = System.currentTimeMillis();
        Timestamp value = (Timestamp) generator.generate(ctx(column, Map.of(), 0));
        // 默认 5 年回看窗口内
        assertThat(value.getTime()).isGreaterThanOrEqualTo(now - 5L * 365 * 24 * 3600 * 1000 - 5_000);
        assertThat(value.getTime()).isLessThanOrEqualTo(now + 5_000);
        Timestamp latest = (Timestamp) generator.generate(ctx(column, Map.of("seconds_ago", 0L), 0));
        assertThat(latest.getTime()).isGreaterThanOrEqualTo(now - 5_000);
        assertThat(latest.getTime()).isLessThanOrEqualTo(now + 5_000);
        assertThat(generator.supports(column)).isTrue();
        assertThat(generator.supports(col("n", "INT", true, List.of()))).isFalse();
    }

    @Test
    void sequenceGeneratorIncrementsAndHonorsStartParam() {
        SequenceGenerator generator = new SequenceGenerator();
        ColumnMetadata column = col("id", "INT", false, List.of());
        assertThat(generator.generate(ctx(column, Map.of(), 0))).isEqualTo(1L);
        assertThat(generator.generate(ctx(column, Map.of(), 1))).isEqualTo(2L);

        SequenceGenerator withStart = new SequenceGenerator();
        assertThat(withStart.generate(ctx(column, Map.of("start", 100L), 0))).isEqualTo(100L);
        assertThat(withStart.generate(ctx(column, Map.of(), 1))).isEqualTo(101L);

        SequenceGenerator customInit = new SequenceGenerator(50L);
        assertThat(customInit.generate(ctx(column, Map.of(), 0))).isEqualTo(50L);
        assertThat(generator.supports(column)).isFalse();
    }

    @Test
    void fakerGeneratorProviderDispatchAndValidation() {
        FakerGenerator generator = new FakerGenerator();
        assertThat(generator.name()).isEqualTo("faker");
        assertThat(FakerGenerator.infer("email")).isEqualTo("email");
        assertThat(FakerGenerator.infer("telephone")).isEqualTo("phone");
        assertThat(FakerGenerator.infer("full_name")).isEqualTo("name");
        assertThat(FakerGenerator.infer("street_address")).isEqualTo("address");
        assertThat(FakerGenerator.infer("company_name")).isEqualTo("company");
        assertThat(FakerGenerator.infer("anything_else")).isEqualTo("name"); // 默认

        ColumnMetadata column = col("email", "VARCHAR", true, List.of());
        assertThat(generator.generate(ctx(column, Map.of("provider", "email"), 0))).isInstanceOf(String.class);
        assertThat(generator.generate(ctx(column, Map.of("provider", "uuid"), 0))).isInstanceOf(String.class);
        assertThat(generator.generate(ctx(column, Map.of("provider", "boolean"), 0))).isInstanceOf(Boolean.class);
        assertThat(generator.generate(ctx(column, Map.of("provider", "date"), 0))).isInstanceOf(String.class);
        assertThat(generator.generate(ctx(column, Map.of("provider", "text"), 0))).isInstanceOf(String.class);

        // 无显式 provider 时按列名推断
        assertThat(generator.generate(ctx(col("full_name", "VARCHAR", true, List.of()), Map.of(), 0)))
                .isInstanceOf(String.class);

        assertThatThrownBy(() -> generator.generate(ctx(column, Map.of("provider", "nope"), 0)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(generator.supports(col("email", "VARCHAR", true, List.of()))).isTrue();
        assertThat(generator.supports(col("n", "INT", false, List.of()))).isFalse();
        assertThat(generator.supports(col("textcol", "TEXT", true, List.of()))).isTrue();
    }

    @Test
    void fakerNameIsModernChineseNameWithoutRareCharacters() {
        FakerGenerator generator = new FakerGenerator();
        ColumnMetadata column = col("name", "VARCHAR", true, List.of());
        for (int i = 0; i < 200; i++) {
            String name = (String) generator.generate(ctx(column, Map.of("provider", "name"), i));
            assertThat(name.length()).isBetween(2, 4);          // 2~4 字，无复姓/生僻长名
            assertThat(name).doesNotContain(" ");               // 无空白
            assertThat(name.codePoints().allMatch(cp -> cp > 0x4E00 && cp < 0x9FFF))
                    .describedAs("姓名应仅含常用汉字：" + name).isTrue();
        }
    }

    @Test
    void fakerEmailIsRfcCompliantAscii() {
        FakerGenerator generator = new FakerGenerator();
        ColumnMetadata column = col("email", "VARCHAR", true, List.of());
        for (int i = 0; i < 200; i++) {
            String email = (String) generator.generate(ctx(column, Map.of("provider", "email"), i));
            assertThat(email).matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
            assertThat(email.length()).isLessThanOrEqualTo(255);
            assertThat(email.codePoints().allMatch(cp -> cp < 0x80))
                    .describedAs("邮箱必须全 ASCII：" + email).isTrue();
        }
    }

    @Test
    void timestampGeneratorAscendsWithinTableAndNeverFuture() {
        TimestampGenerator generator = new TimestampGenerator();
        ColumnMetadata column = col("created_at", "TIMESTAMP", false, List.of());
        long now = System.currentTimeMillis();
        long window = 5L * 365 * 24 * 3600 * 1000;
        Timestamp first = null;
        Timestamp last = null;
        for (int row = 0; row < 10; row++) {
            Timestamp value = (Timestamp) generator.generate(ctx(column, Map.of(), row));
            assertThat(value.getTime()).isLessThanOrEqualTo(now + 5_000);
            assertThat(value.getTime()).isGreaterThanOrEqualTo(now - window - 5_000);
            if (row == 0) {
                first = value;
            }
            if (row == 9) {
                last = value;
            }
        }
        assertThat(last.getTime()).isGreaterThan(first.getTime()); // 表内时间随行号单调
        // 窗口归零时恒为当前时间
        Timestamp latest = (Timestamp) generator.generate(ctx(column, Map.of("seconds_ago", 0L), 0));
        assertThat(latest.getTime()).isLessThanOrEqualTo(now + 5_000);
        assertThat(latest.getTime()).isGreaterThanOrEqualTo(now - 5_000);
    }

    @Test
    void chineseNameFollowsGenderCorpus() {
        for (int i = 0; i < 300; i++) {
            String male = ChinesePersonData.randomNameForGender("男");
            assertThat(ChinesePersonData.genderOfName(male)).isEqualTo("男");
            String female = ChinesePersonData.randomNameForGender("女");
            assertThat(ChinesePersonData.genderOfName(female)).isEqualTo("女");
        }
        assertThat(ChinesePersonData.genderOfName("张伟")).isEqualTo("男");
        assertThat(ChinesePersonData.genderOfName("李芳")).isEqualTo("女");
        assertThat(ChinesePersonData.genderOfName("张三")).isNull(); // 语料外姓名不强制判定
    }
}