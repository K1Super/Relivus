package com.relivus.generator;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.schema.model.CheckConstraintMetadata;
import com.relivus.schema.model.CheckType;
import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.ForeignKeyMetadata;
import com.relivus.schema.model.TableMetadata;
import com.relivus.ai.AiHttpClient;
import com.relivus.config.RelivusProperties;
import com.relivus.service.IAiConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 值生成器解析工厂。
 *
 * <p>按「用户配置 > 自增跳过 > 外键列 > ENUM 列 > CHECK 约束 > 非自增主键 > 列名启发式 > 类型默认」
 * 顺序为列选择生成器。所有实例均为线程安全单例（sequence 有状态，单独新建）。
 */
@Component
public final class ValueGeneratorFactory {

    /** 解析产物：生成器 + 默认参数（generate 时与用户参数合并，用户优先）。 */
    public record ResolvedGenerator(ValueGenerator generator, Map<String, Object> params) {

        public Object generate(GenerationContext context) {
            Map<String, Object> merged = mergeParams(context.params(), params);
            GenerationContext effective = new GenerationContext(context.table(), context.column(),
                    context.rowIndex(), context.totalRows(), merged, context.dialect());
            return generator.generate(effective);
        }

        private static Map<String, Object> mergeParams(Map<String, Object> user, Map<String, Object> defaults) {
            if ((defaults == null || defaults.isEmpty())) {
                return user == null ? Map.of() : user;
            }
            Map<String, Object> merged = new HashMap<>(defaults);
            if (user != null) {
                merged.putAll(user);
            }
            return Map.copyOf(merged);
        }
    }

    private static final EnumGenerator ENUM = new EnumGenerator();
    private static final RandomIntGenerator RANDOM_INT = new RandomIntGenerator();
    private static final RandomDecimalGenerator RANDOM_DECIMAL = new RandomDecimalGenerator();
    private static final FakerGenerator FAKER = new FakerGenerator();
    private static final TimestampGenerator TIMESTAMP = new TimestampGenerator();
    private static final ForeignKeyGenerator FOREIGN_KEY = new ForeignKeyGenerator();

    private static final Map<String, ValueGenerator> REGISTRY = Map.of(
            "random_int", RANDOM_INT,
            "random_decimal", RANDOM_DECIMAL,
            "faker", FAKER,
            "enum", ENUM,
            "fixed", new FixedValueGenerator(),
            "regex", new RegexGenerator(),
            "timestamp", TIMESTAMP,
            "foreign_key", FOREIGN_KEY);

    private static final List<String> EMAIL_COLUMNS = List.of("email", "mail");
    private static final List<String> PHONE_COLUMNS = List.of("phone", "mobile", "tel");
    private static final List<String> NAME_COLUMNS = List.of("name", "nickname", "full_name", "user_name", "username");
    private static final List<String> ADDRESS_COLUMNS = List.of("address", "addr", "street", "city", "province", "region");
    private static final List<String> COMPANY_COLUMNS = List.of("company", "org", "organization", "vendor");
    private static final List<String> TIME_COLUMNS = List.of("created_at", "updated_at", "deleted_at", "create_time", "update_time");
    private static final List<String> MONEY_COLUMNS = List.of("price", "amount", "salary", "total", "balance", "fee", "cost");
    private static final List<String> COUNT_COLUMNS = List.of("count", "num", "quantity", "stock", "year_month");
    private static final List<String> AGE_COLUMNS = List.of("age");

    /** 无法生成且可空的类型（置 NULL 跳过）。 */
    private static final List<String> SKIPPABLE_TYPES = List.of("JSON", "BLOB", "BINARY", "BYTEA", "GEOMETRY");

    private AiHttpClient aiHttpClient;
    private IAiConfigService aiConfigService;
    private RelivusProperties relivusProperties;

    /**
     * AI 依赖注入点。{@code required = false}：无 Spring 容器（单测直接 {@code new}）或组件缺失时，
     * 工厂仍可实例化，仅在 "ai" 分支按空引用抛 {@code VALIDATION_FAILED}。
     */
    @Autowired(required = false)
    public void setAiSupport(AiHttpClient aiHttpClient, IAiConfigService aiConfigService,
                             RelivusProperties relivusProperties) {
        this.aiHttpClient = aiHttpClient;
        this.aiConfigService = aiConfigService;
        this.relivusProperties = relivusProperties;
    }

    /** 解析列生成器（无日志回调，委托 5 参版本）。 */
    public ResolvedGenerator resolve(ColumnMetadata column, TableMetadata table,
                                     String userGenerator, Map<String, Object> userParams) {
        return resolve(column, table, userGenerator, userParams, null);
    }

    /**
     * 解析列生成器。
     *
     * @param listener 运行日志回调（AI 降级告警用，可为 null）
     * @return null 表示该列跳过生成（自增列由数据库填充 / 不可生成的可空列置 NULL）
     */
    public ResolvedGenerator resolve(ColumnMetadata column, TableMetadata table,
                                     String userGenerator, Map<String, Object> userParams,
                                     GenerationEngineListener listener) {
        // 1. 用户显式配置（最高优先级）
        if (userGenerator != null && !userGenerator.isBlank()) {
            return resolveUserConfigured(userGenerator, userParams, listener);
        }
        // 2. 自增列跳过，由数据库生成
        if (column.autoIncrement()) {
            return null;
        }
        // 3. 外键列：由引擎注入采样值
        if (isForeignKeyColumn(table, column.columnName())) {
            return new ResolvedGenerator(FOREIGN_KEY, Map.of());
        }
        // 4. ENUM 列
        if (column.isEnum()) {
            return new ResolvedGenerator(ENUM, Map.of());
        }
        // 5. CHECK 约束（IN / RANGE 单列形式）
        ResolvedGenerator fromCheck = resolveFromCheck(column, table);
        if (fromCheck != null) {
            return fromCheck;
        }
        // 6. 非自增主键：整型序列 / 字符 UUID
        ColumnMetadata pk = table.primaryKeyColumn();
        if (pk != null && pk.columnName().equals(column.columnName())) {
            String t = column.dataType().toUpperCase(Locale.ROOT);
            if (t.contains("INT")) {
                return new ResolvedGenerator(new SequenceGenerator(), Map.of());
            }
            if (t.contains("CHAR") || t.contains("TEXT") || t.contains("UUID")) {
                return new ResolvedGenerator(FAKER, Map.of("provider", "uuid"));
            }
        }
        // 7. 列名启发式
        ResolvedGenerator fromName = resolveFromName(column);
        if (fromName != null) {
            return fromName;
        }
        // 8. 类型默认
        return resolveFromType(column);
    }

    private ResolvedGenerator resolveUserConfigured(String userGenerator, Map<String, Object> userParams,
                                                    GenerationEngineListener listener) {
        if ("sequence".equalsIgnoreCase(userGenerator)) {
            return new ResolvedGenerator(new SequenceGenerator(), userParams == null ? Map.of() : userParams);
        }
        if ("ai".equalsIgnoreCase(userGenerator)) {
            if (aiHttpClient == null || aiConfigService == null) {
                throw new RelivusException(ErrorCode.VALIDATION_FAILED, "AI 生成器组件未初始化");
            }
            String prompt = userParams == null ? null : (String) userParams.get("prompt");
            if (prompt == null || prompt.isBlank()) {
                throw new RelivusException(ErrorCode.VALIDATION_FAILED, "ai: param 'prompt' is required");
            }
            int size = relivusProperties == null ? 64 : relivusProperties.getAi().getBatchSize();
            return new ResolvedGenerator(
                    new AiGenerator(prompt, aiHttpClient, aiConfigService::resolveActive, listener, size),
                    Map.of());
        }
        ValueGenerator generator = REGISTRY.get(userGenerator.toLowerCase(Locale.ROOT));
        if (generator == null) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                    "未知生成器 '" + userGenerator + "'（支持：" + String.join("/", REGISTRY.keySet()) + "/sequence）");
        }
        return new ResolvedGenerator(generator, userParams == null ? Map.of() : userParams);
    }

    private static boolean isForeignKeyColumn(TableMetadata table, String columnName) {
        for (ForeignKeyMetadata fk : table.foreignKeys()) {
            if (fk.columnName().equals(columnName)) {
                return true;
            }
        }
        return false;
    }

    private ResolvedGenerator resolveFromCheck(ColumnMetadata column, TableMetadata table) {
        for (CheckConstraintMetadata check : table.checkConstraints()) {
            if (check.columns().size() != 1 || !check.columns().get(0).equals(column.columnName())) {
                continue;
            }
            switch (check.type()) {
                case IN -> {
                    return new ResolvedGenerator(ENUM, Map.of("values", check.allowedValues()));
                }
                case RANGE -> {
                    if (check.minValue() == null || check.maxValue() == null) {
                        continue;
                    }
                    String t = column.dataType().toUpperCase(Locale.ROOT);
                    if (t.contains("INT")) {
                        return new ResolvedGenerator(RANDOM_INT,
                                Map.of("min", check.minValue().longValueExact(), "max", check.maxValue().longValueExact()));
                    }
                    if (RANDOM_DECIMAL.supports(column)) {
                        return new ResolvedGenerator(RANDOM_DECIMAL,
                                Map.of("min", check.minValue().doubleValue(), "max", check.maxValue().doubleValue()));
                    }
                }
                default -> {
                    // UNKNOWN：不猜测语义，落入后续默认策略
                }
            }
        }
        return null;
    }

    private ResolvedGenerator resolveFromName(ColumnMetadata column) {
        String n = column.columnName().toLowerCase(Locale.ROOT);
        if (containsAny(n, EMAIL_COLUMNS)) {
            return new ResolvedGenerator(FAKER, Map.of("provider", "email"));
        }
        if (containsAny(n, PHONE_COLUMNS)) {
            return new ResolvedGenerator(new RegexGenerator(), Map.of("pattern", "1[3-9]\\d{9}"));
        }
        if (containsAny(n, NAME_COLUMNS)) {
            return new ResolvedGenerator(FAKER, Map.of("provider", "name"));
        }
        if (containsAny(n, ADDRESS_COLUMNS)) {
            return new ResolvedGenerator(FAKER, Map.of("provider", "address"));
        }
        if (containsAny(n, COMPANY_COLUMNS)) {
            return new ResolvedGenerator(FAKER, Map.of("provider", "company"));
        }
        if (containsAny(n, TIME_COLUMNS)) {
            return new ResolvedGenerator(TIMESTAMP, Map.of());
        }
        if (containsAny(n, MONEY_COLUMNS)) {
            return new ResolvedGenerator(RANDOM_DECIMAL, Map.of("min", 1.0D, "max", 100000.0D, "scale", 2));
        }
        if (containsAny(n, AGE_COLUMNS)) {
            return new ResolvedGenerator(RANDOM_INT, Map.of("min", 18L, "max", 70L));
        }
        if (containsAny(n, COUNT_COLUMNS)) {
            return new ResolvedGenerator(RANDOM_INT, Map.of("min", 1L, "max", 1000L));
        }
        if (n.startsWith("is_") || n.startsWith("has_") || n.endsWith("_flag") || n.endsWith("_enabled")) {
            return new ResolvedGenerator(FAKER, Map.of("provider", "boolean"));
        }
        return null;
    }

    private ResolvedGenerator resolveFromType(ColumnMetadata column) {
        String t = column.dataType().toUpperCase(Locale.ROOT);
        if (containsAny(t, SKIPPABLE_TYPES)) {
            if (column.nullable()) {
                return null;
            }
            throw new RelivusException(ErrorCode.GENERATION_FAILED,
                    "无法为 NOT NULL 列自动生成值：" + column.columnName() + "（类型 " + column.dataType() + "）");
        }
        if (t.contains("INT")) {
            return new ResolvedGenerator(RANDOM_INT, Map.of());
        }
        if (RANDOM_DECIMAL.supports(column)) {
            return new ResolvedGenerator(RANDOM_DECIMAL, Map.of());
        }
        if (t.contains("BOOL") || t.equals("BIT")) {
            return new ResolvedGenerator(FAKER, Map.of("provider", "boolean"));
        }
        if (TIMESTAMP.supports(column)) {
            return new ResolvedGenerator(TIMESTAMP, Map.of());
        }
        if (FAKER.supports(column)) {
            return new ResolvedGenerator(FAKER, Map.of());
        }
        if (column.nullable()) {
            return null;
        }
        throw new RelivusException(ErrorCode.GENERATION_FAILED,
                "无法为 NOT NULL 列自动生成值：" + column.columnName() + "（类型 " + column.dataType() + "）");
    }

    private static boolean containsAny(String value, List<String> keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}