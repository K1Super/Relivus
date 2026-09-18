package com.relivus.generator;

import com.relivus.ai.AiHttpClient;
import com.relivus.ai.AiProviderConfig;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.schema.model.ColumnMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 列级生成器单元测试：批缓存 / 类型规整 / 失败降级仅一次告警 / 降级后不回试 / 配置缺失原样上抛。
 */
class AiGeneratorTest {

    private static ColumnMetadata col(String name, String type) {
        return new ColumnMetadata(name, type, false, null, false, List.of());
    }

    private static GenerationContext ctx(ColumnMetadata column) {
        return new GenerationContext("users", column, 0, 10, Map.of(), null);
    }

    /** 记录 WARN 次数的最小监听器。 */
    private static final class RecordingListener implements GenerationEngineListener {
        int warnCount;

        @Override
        public void onProgress(String table, long insertedRows, long totalRows) {
        }

        @Override
        public void onLog(Severity severity, String message) {
            if (severity == Severity.WARN) {
                warnCount++;
            }
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    private static AiHttpClient fixedClient(List<String> values, AtomicInteger calls) {
        return new AiHttpClient() {
            @Override
            public List<String> completeBatch(AiProviderConfig cfg, String prompt, int count) {
                calls.incrementAndGet();
                return values;
            }

            @Override
            public String ping(AiProviderConfig cfg) {
                return "ok";
            }
        };
    }

    private static AiHttpClient failingClient(RelivusException error, AtomicInteger calls) {
        return new AiHttpClient() {
            @Override
            public List<String> completeBatch(AiProviderConfig cfg, String prompt, int count) {
                calls.incrementAndGet();
                throw error;
            }

            @Override
            public String ping(AiProviderConfig cfg) {
                throw error;
            }
        };
    }

    private static AiProviderConfig config() {
        return new AiProviderConfig("n", "http://x", "k", "m");
    }

    @Test
    void batchCacheTriggersOneCallThenConsumesCache() {
        AtomicInteger calls = new AtomicInteger();
        AiGenerator generator = new AiGenerator("p", fixedClient(List.of("v1", "v2", "v3"), calls),
                AiGeneratorTest::config, null, 3);
        ColumnMetadata column = col("nickname", "VARCHAR");

        Object first = generator.generate(ctx(column));
        assertThat(first).isEqualTo("v1");
        assertThat(calls.get()).isEqualTo(1);

        assertThat(generator.generate(ctx(column))).isEqualTo("v2");
        assertThat(generator.generate(ctx(column))).isEqualTo("v3");
        assertThat(calls.get()).isEqualTo(1);

        // 缓存耗尽后触发第二次网络调用
        assertThat(generator.generate(ctx(column))).isEqualTo("v1");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void intColumnCoercesStringNumberToLong() {
        AtomicInteger calls = new AtomicInteger();
        AiGenerator generator = new AiGenerator("p", fixedClient(List.of("42"), calls),
                AiGeneratorTest::config, null, 1);
        Object value = generator.generate(ctx(col("age", "INT")));
        assertThat(value).isInstanceOf(Long.class).isEqualTo(42L);
    }

    @Test
    void upstreamFailureDegradesToLocalAndWarnsOnce() {
        AtomicInteger calls = new AtomicInteger();
        RecordingListener listener = new RecordingListener();
        AiGenerator generator = new AiGenerator("p",
                failingClient(new RelivusException(ErrorCode.AI_UPSTREAM_FAILED, "boom"), calls),
                AiGeneratorTest::config, listener, 64);
        ColumnMetadata column = col("nickname", "VARCHAR");

        Object first = generator.generate(ctx(column));
        assertThat(first).isInstanceOf(String.class);
        assertThat((String) first).isNotBlank();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(listener.warnCount).isEqualTo(1);

        // 降级后不回试：后续取本地缓存值，网络调用次数不变
        generator.generate(ctx(column));
        generator.generate(ctx(column));
        assertThat(calls.get()).isEqualTo(1);
        assertThat(listener.warnCount).isEqualTo(1);
    }

    @Test
    void configNotFoundPropagatesWithoutDegrading() {
        AtomicInteger calls = new AtomicInteger();
        AiGenerator generator = new AiGenerator("p", fixedClient(List.of("x"), calls), () -> {
            throw new RelivusException(ErrorCode.AI_CONFIG_NOT_FOUND, "none");
        }, null, 1);

        assertThatThrownBy(() -> generator.generate(ctx(col("nickname", "VARCHAR"))))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.AI_CONFIG_NOT_FOUND.getCode());
        assertThat(calls.get()).isZero();
    }
}