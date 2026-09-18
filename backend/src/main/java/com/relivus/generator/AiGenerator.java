package com.relivus.generator;

import com.relivus.ai.AiHttpClient;
import com.relivus.ai.AiProviderConfig;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.schema.model.ColumnMetadata;
import net.datafaker.Faker;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * AI 列级值生成器（DOC-03 / DOC-11：列级 AI 生成 + 失败降级）。
 *
 * <p>生命周期：每个「表 × 列 × 任务」由 {@link ValueGeneratorFactory} 新建一个实例，实例不共享，
 * 故 {@code cache}/{@code degraded} 等可变状态天然线程安全、无需加锁。首次 {@code generate}
 * 触发一次批量网络调用填充缓存，随后消耗缓存；上游失败（或 AI 配置缺失被区分处理）后降级为
 * 本地假数据，降级后不再回试。
 */
public class AiGenerator implements ValueGenerator {

    private final String prompt;
    private final AiHttpClient client;
    private final Supplier<AiProviderConfig> configSupplier;
    private final GenerationEngineListener listener;
    private final int batchSize;
    private final ArrayDeque<Object> cache = new ArrayDeque<>();
    private final Faker faker = new Faker(new Locale("zh-CN"));
    private boolean degraded = false;

    /**
     * @param prompt         生成提示词
     * @param client         AI HTTP 客户端
     * @param configSupplier 每次批量请求前解析激活配置（抛 AI_CONFIG_NOT_FOUND 视为整体失败）
     * @param listener       运行日志回调（可为 null，仅用于降级告警）
     * @param batchSize      单次批量请求的值数量
     */
    public AiGenerator(String prompt, AiHttpClient client, Supplier<AiProviderConfig> configSupplier,
                       GenerationEngineListener listener, int batchSize) {
        this.prompt = prompt;
        this.client = client;
        this.configSupplier = configSupplier;
        this.listener = listener;
        this.batchSize = batchSize;
    }

    @Override
    public String name() {
        return "ai";
    }

    @Override
    public boolean supports(ColumnMetadata column) {
        return false;
    }

    @Override
    public Object generate(GenerationContext context) {
        ensureCache(context);
        Object value = cache.poll();
        return coerce(context, value);
    }

    private void ensureCache(GenerationContext context) {
        if (!cache.isEmpty()) {
            return;
        }
        if (degraded) {
            fillLocal(context);
            return;
        }
        AiProviderConfig config;
        try {
            config = configSupplier.get(); // AI_CONFIG_NOT_FOUND 原样上抛（首次即失败，不算降级）
        } catch (RelivusException e) {
            throw e;
        }
        try {
            List<String> values = client.completeBatch(config, prompt, batchSize);
            if (values == null || values.isEmpty()) {
                markDegraded(context, "AI 返回空结果");
                fillLocal(context);
                return;
            }
            cache.addAll(values);
        } catch (RelivusException e) {
            if (e.getErrorCode() == ErrorCode.AI_CONFIG_NOT_FOUND) {
                throw e;
            }
            markDegraded(context, "AI 生成失败：" + e.getMessage());
            fillLocal(context);
        }
    }

    /** 记录一次降级告警（仅首次，degraded 一旦置位不再回试 AI）。 */
    private void markDegraded(GenerationContext context, String reason) {
        if (degraded) {
            return;
        }
        degraded = true;
        if (listener != null) {
            listener.onLog(GenerationEngineListener.Severity.WARN,
                    "AI 生成降级为本地假数据：表 " + context.table() + " 列 "
                            + context.column().columnName() + "（" + reason + "）");
        }
    }

    private void fillLocal(GenerationContext context) {
        for (int i = 0; i < Math.max(1, batchSize); i++) {
            cache.add(localValue(context));
        }
    }

    /** 语义化降级假数据：按列名启发式 + 整型默认，尽量贴近真实形态。 */
    private Object localValue(GenerationContext context) {
        String name = context.column().columnName().toLowerCase(Locale.ROOT);
        String type = context.column().dataType().toUpperCase(Locale.ROOT);
        if (name.contains("name")) {
            return ChinesePersonData.randomName();
        }
        if (name.contains("email") || name.contains("mail")) {
            return ChinesePersonData.randomEmail();
        }
        if (name.contains("phone") || name.contains("mobile") || name.contains("tel")) {
            return faker.regexify("1[3-9]\\d{9}");
        }
        if (name.contains("address") || name.contains("addr")
                || name.contains("city") || name.contains("street")) {
            return faker.address().fullAddress();
        }
        if (type.contains("INT")) {
            return ThreadLocalRandom.current().nextLong(18, 71);
        }
        return faker.lorem().word();
    }

    /** 类型规整：INT 列转 Long；其余类型 Number/Boolean/List/Map 转字符串，String 原样。 */
    private Object coerce(GenerationContext context, Object value) {
        String type = context.column().dataType().toUpperCase(Locale.ROOT);
        if (type.contains("INT")) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String s) {
                try {
                    return Long.parseLong(s.trim());
                } catch (NumberFormatException e) {
                    throw new RelivusException(ErrorCode.VALIDATION_FAILED, "AI 返回值无法转换为整数");
                }
            }
            throw new RelivusException(ErrorCode.VALIDATION_FAILED, "AI 返回值无法转换为整数");
        }
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return value;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof List || value instanceof Map) {
            return String.valueOf(value);
        }
        return String.valueOf(value);
    }
}