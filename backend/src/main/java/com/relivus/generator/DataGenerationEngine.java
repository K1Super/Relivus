package com.relivus.generator;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dialect.DatabaseDialect;
import com.relivus.dto.GenerationConfig;
import com.relivus.dto.GenerationConfig.TableConfig;
import com.relivus.dto.GenerationConfig.TableConfig.ColumnConfig;
import com.relivus.generator.GenerationEngineListener.Severity;
import com.relivus.schema.SchemaIntrospector;
import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.ForeignKeyMetadata;
import com.relivus.schema.model.TableMetadata;
import com.relivus.schema.model.UniqueIndexMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 数据生成引擎。
 *
 * <p>执行流程：内省 → 按配置过滤并校验 → 依赖图 → Kahn 拓扑排序 → 循环依赖 FK 可空校验
 * → 可选清空 → 非循环表按拓扑序生成（本地唯一检测 + 数据库唯一索引兜底）→ 循环表两阶段插入
 * （阶段一 FK 置 NULL，阶段二按主键回填）。逐批回调进度与取消令牌。
 */
@Component
public class DataGenerationEngine {

    private static final Logger LOG = LoggerFactory.getLogger(DataGenerationEngine.class);

    /** 单行唯一冲突最大重试次数。 */
    public static final int MAX_ROW_RETRY = 100;
    /** 连续冲突达到该阈值判定为低基数无法收敛。 */
    public static final int LOW_CARDINALITY_FAIL_THRESHOLD = 10;

    private final ValueGeneratorFactory factory;
    private final SchemaIntrospector introspector;

    public DataGenerationEngine(ValueGeneratorFactory factory, SchemaIntrospector introspector) {
        this.factory = factory;
        this.introspector = introspector;
    }

    /** 外键列处理模式。 */
    private enum FkMode {
        /** 采样父表真实主键（普通表）。 */
        SAMPLE,
        /** 置 NULL（循环表阶段一）。 */
        NULL
    }

    /** 单表列计划：参与插入的列、对应生成器、列名定位。 */
    private record ColumnPlan(List<ColumnMetadata> columns, List<ValueGeneratorFactory.ResolvedGenerator> generators,
                              Map<String, Integer> indexByColumn) {

        int columnIndex(String columnName) {
            return indexByColumn.getOrDefault(columnName, -1);
        }
    }

    /** 单表插入产物：每行定位键值（自增主键为回填 ID，非自增主键为生成值）。 */
    private record TableInsertResult(List<Object> rowKeys, long inserted) {
    }

    /**
     * 执行一次数据生成。
     *
     * @param dataSource 目标库数据源（连接已由上层解析）
     * @param dialect    目标库方言
     * @param config     生成配置
     * @param listener   进度/日志/取消回调，可为 null
     */
    public GenerationRunResult execute(DataSource dataSource, DatabaseDialect dialect,
                                       GenerationConfig config, GenerationEngineListener listener) {
        GenerationEngineListener l = listener == null ? new NoopListener() : listener;
        long start = System.currentTimeMillis();
        JdbcTemplate jt = new JdbcTemplate(dataSource);
        SamplingStrategy strategy = parseSampling(config.samplingStrategyOrDefault());

        Map<String, TableConfig> configs = new LinkedHashMap<>();
        for (TableConfig tc : config.tables()) {
            configs.put(tc.table(), tc);
        }

        List<TableMetadata> allTables = introspect(dataSource, dialect);
        Map<String, TableMetadata> selected = new LinkedHashMap<>();
        for (TableMetadata table : allTables) {
            if (configs.containsKey(table.tableName())) {
                selected.put(table.tableName(), table);
            }
        }
        List<String> missing = configs.keySet().stream().filter(t -> !selected.containsKey(t)).toList();
        if (!missing.isEmpty()) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED, "表不存在：" + missing);
        }

        // 依赖图 + 拓扑排序
        TableDependencyGraph graph = new TableDependencyGraph();
        graph.build(new ArrayList<>(selected.values()));
        TopologicalSorter.TopologicalSortResult sort = TopologicalSorter.sort(graph);
        if (sort.hasCycle()) {
            validateCircularFkNullable(selected, sort.cycles());
        }

        if (config.truncateBeforeOrDefault()) {
            truncateTables(jt, dialect, new ArrayList<>(selected.values()));
            l.onLog(Severity.WARN, "已按配置清空目标表：" + selected.keySet());
        }

        UniqueConstraintChecker checker = new UniqueConstraintChecker();
        Map<String, Long> rowsByTable = new LinkedHashMap<>();
        try {
            Set<String> cyclic = new LinkedHashSet<>();
            for (List<String> cycle : sort.cycles()) {
                cyclic.addAll(cycle);
            }
            int batchSize = config.batchSizeOrDefault();
            // 普通表：以拓扑顺序为准，附录遗漏表（环内表跳过）
            LinkedHashSet<String> ordered = new LinkedHashSet<>(sort.order());
            ordered.addAll(selected.keySet());
            for (String tableName : ordered) {
                if (cyclic.contains(tableName)) {
                    continue;
                }
                TableInsertResult result = insertTable(jt, dialect, selected.get(tableName), configs.get(tableName),
                        batchSize, l, checker, FkMode.SAMPLE, null, strategy);
                rowsByTable.put(tableName, result.inserted());
                l.onLog(Severity.INFO, "表 " + tableName + " 生成完成，插入 " + result.inserted() + " 行");
            }
            // 循环依赖表：两阶段插入
            for (List<String> cycle : sort.cycles()) {
                Map<String, Long> cycleRows = insertCycle(jt, dialect, cycle, selected, configs,
                        batchSize, l, checker, strategy);
                rowsByTable.putAll(cycleRows);
            }
            return new GenerationRunResult(rowsByTable, System.currentTimeMillis() - start);
        } finally {
            checker.reset();
        }
    }

    private List<TableMetadata> introspect(DataSource dataSource, DatabaseDialect dialect) {
        try (Connection connection = dataSource.getConnection()) {
            return introspector.introspect(connection, dialect);
        } catch (SQLException e) {
            throw new RelivusException(ErrorCode.CONNECTION_FAILED, "目标库连接失败：" + e.getMessage(), e);
        }
    }

    private static SamplingStrategy parseSampling(String value) {
        try {
            return SamplingStrategy.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED, "采样策略无效：" + value);
        }
    }

    /** 循环依赖中加入环的 FK 列不可空时拒绝执行。 */
    private void validateCircularFkNullable(Map<String, TableMetadata> selected, List<List<String>> cycles) {
        for (List<String> cycle : cycles) {
            Set<String> cycleSet = new LinkedHashSet<>(cycle);
            for (String tableName : cycle) {
                for (ForeignKeyMetadata fk : selected.get(tableName).foreignKeys()) {
                    if (cycleSet.contains(fk.refTable()) && !fk.nullable()) {
                        throw new RelivusException(ErrorCode.CIRCULAR_FK_NOT_NULL,
                                "循环依赖外键 " + tableName + "." + fk.columnName()
                                        + " → " + fk.refTable() + " 为 NOT NULL，无法两阶段插入");
                    }
                }
            }
        }
    }

    private void truncateTables(JdbcTemplate jt, DatabaseDialect dialect, List<TableMetadata> tables) {
        if ("mysql".equalsIgnoreCase(dialect.name())) {
            jt.execute("SET FOREIGN_KEY_CHECKS=0");
            try {
                for (TableMetadata table : tables) {
                    jt.update("DELETE FROM " + dialect.quoteIdentifier(table.tableName()));
                }
            } finally {
                jt.execute("SET FOREIGN_KEY_CHECKS=1");
            }
        } else {
            for (TableMetadata table : tables) {
                jt.execute("TRUNCATE TABLE " + dialect.quoteIdentifier(table.tableName()) + " CASCADE");
            }
        }
    }

    /** 单表插入（普通模式）。 */
    private TableInsertResult insertTable(JdbcTemplate jt, DatabaseDialect dialect, TableMetadata meta,
                                          TableConfig cfg, int batchSize, GenerationEngineListener l,
                                          UniqueConstraintChecker checker, FkMode fkMode,
                                          List<Object> externalRowKeys, SamplingStrategy strategy) {
        ColumnPlan plan = planColumns(meta, cfg, l);
        long total = cfg.rowCount();
        ForeignKeySampler sampler = new ForeignKeySampler(jt.getDataSource(), dialect);
        List<Object[]> batch = new ArrayList<>(Math.min(batchSize, 1024));
        List<Object> rowKeys = externalRowKeys != null ? externalRowKeys : new ArrayList<>();
        long inserted = 0;
        while (inserted < total) {
            checkCancelled(l);
            long baseIndex = inserted;
            // rowIndex 用全局行号（inserted + 批内已生成行数），保证批内各行的列生成器（如时间单调）正确递进
            Object[] row = generateRow(meta, plan, cfg, inserted + batch.size(), total, checker, fkMode, dialect, sampler, strategy);
            if (fkMode == FkMode.NULL) {
                rowKeys.add(extractRowKey(meta, plan, dialect, row));
            }
            batch.add(row);
            if (batch.size() >= batchSize || inserted + batch.size() >= total) {
                if (fkMode == FkMode.NULL) {
                    flushWithKeys(jt, dialect, meta, plan, batch, rowKeys);
                } else {
                    flushInsert(jt, dialect, meta, plan, batch,
                            i -> generateRow(meta, plan, cfg, baseIndex + i, total, checker,
                                    FkMode.SAMPLE, dialect, sampler, strategy));
                }
                inserted += batch.size();
                batch = new ArrayList<>(Math.min(batchSize, 1024));
                l.onProgress(meta.tableName(), inserted, total);
            }
        }
        return new TableInsertResult(rowKeys, inserted);
    }

    /** 提取该行的目标库定位键：自增主键待回填（占位 null），否则取生成的主键值。 */
    private Object extractRowKey(TableMetadata meta, ColumnPlan plan, DatabaseDialect dialect, Object[] row) {
        ColumnMetadata pk = meta.primaryKeyColumn();
        if (pk == null) {
            throw new RelivusException(ErrorCode.GENERATION_FAILED,
                    "循环依赖表 " + meta.tableName() + " 无主键，无法执行两阶段回填");
        }
        if (pk.autoIncrement()) {
            return null; // 由 flushWithKeys 以返回 ID 回填
        }
        return row[plan.columnIndex(pk.columnName())];
    }

    /** 需要回填主键的行批量插入：自增列用 RETURN_GENERATED_KEYS 回填到 rowKeys。 */
    private void flushWithKeys(JdbcTemplate jt, DatabaseDialect dialect, TableMetadata meta,
                               ColumnPlan plan, List<Object[]> batch, List<Object> rowKeys) {
        ColumnMetadata pk = meta.primaryKeyColumn();
        if (pk == null || !pk.autoIncrement()) {
            flushInsert(jt, dialect, meta, plan, batch, null);
            return;
        }
        BatchInserter inserter = new BatchInserter(jt, dialect);
        List<Long> ids = inserter.insertReturningIds(meta.tableName(), plan.columns(), batch, Math.max(1, batch.size()));
        for (int i = 0; i < batch.size(); i++) {
            rowKeys.set(rowKeys.size() - batch.size() + i, ids.get(i));
        }
    }

    /** 批量插入 + 数据库唯一索引兜底（本地 LRU 淘汰后的真实冲突逐行重插重生成）。 */
    private void flushInsert(JdbcTemplate jt, DatabaseDialect dialect, TableMetadata meta,
                             ColumnPlan plan, List<Object[]> batch, RowRegenerator regenerator) {
        BatchInserter inserter = new BatchInserter(jt, dialect);
        try {
            inserter.insertBulk(meta.tableName(), plan.columns(), batch, Math.max(1, batch.size()));
        } catch (DataIntegrityViolationException e) {
            LOG.warn("批量插入触发数据库唯一约束，逐行重插并重生成冲突行：table={}", meta.tableName());
            for (int i = 0; i < batch.size(); i++) {
                Object[] row = batch.get(i);
                int attempts = 0;
                int consecutiveDbFailures = 0;
                while (true) {
                    try {
                        jt.update(dialect.buildInsertSql(meta.tableName(), plan.columns()), row);
                        break;
                    } catch (DataIntegrityViolationException ex) {
                        consecutiveDbFailures++;
                        attempts++;
                        if (consecutiveDbFailures >= LOW_CARDINALITY_FAIL_THRESHOLD) {
                            throw new UniqueConstraintException(
                                    "表 " + meta.tableName() + " 行唯一冲突连续 " + consecutiveDbFailures
                                            + " 次未收敛（数据库兜底，疑似低基数）", ex);
                        }
                        if (attempts >= MAX_ROW_RETRY || regenerator == null) {
                            throw new UniqueConstraintException(
                                    "表 " + meta.tableName() + " 数据库唯一约束冲突（本地检测未覆盖）", ex);
                        }
                        row = regenerator.regenerate(i);
                    }
                }
            }
        }
    }

    /** 冲突行重生成接口：DB 兜底定位到冲突行后，重新生成该行值。 */
    @FunctionalInterface
    private interface RowRegenerator {
        Object[] regenerate(int batchIndex);
    }

    /** 循环依赖强连通分量两阶段插入：阶段一全表 FK 置 NULL，阶段二按主键回填。 */
    private Map<String, Long> insertCycle(JdbcTemplate jt, DatabaseDialect dialect, List<String> cycle,
                                          Map<String, TableMetadata> selected, Map<String, TableConfig> configs,
                                          int batchSize, GenerationEngineListener l,
                                          UniqueConstraintChecker checker, SamplingStrategy strategy) {
        Map<String, Long> rowsByTable = new LinkedHashMap<>();
        Map<String, List<Object>> rowKeysByTable = new LinkedHashMap<>();
        for (String tableName : cycle) {
            TableInsertResult result = insertTable(jt, dialect, selected.get(tableName), configs.get(tableName),
                    batchSize, l, checker, FkMode.NULL, new ArrayList<>(), strategy);
            rowKeysByTable.put(tableName, result.rowKeys());
            rowsByTable.put(tableName, result.inserted());
            l.onLog(Severity.INFO, "循环表 " + tableName + " 阶段一完成，插入 " + result.inserted() + " 行");
        }
        // 阶段二：SCC 内部 FK 采样父表主键回填
        ForeignKeySampler sampler = new ForeignKeySampler(jt.getDataSource(), dialect);
        Set<String> cycleSet = new LinkedHashSet<>(cycle);
        for (String tableName : cycle) {
            TableMetadata meta = selected.get(tableName);
            List<Object> rowKeys = rowKeysByTable.get(tableName);
            ColumnMetadata pk = meta.primaryKeyColumn();
            if (pk == null) {
                throw new RelivusException(ErrorCode.GENERATION_FAILED,
                        "循环依赖表 " + tableName + " 无主键，无法回填外键");
            }
            for (ForeignKeyMetadata fk : meta.foreignKeys()) {
                if (!cycleSet.contains(fk.refTable())) {
                    continue;
                }
                String sql = dialect.buildUpdateSql(tableName, List.of(fk.columnName()),
                        dialect.quoteIdentifier(pk.columnName()) + " = ?");
                List<Object[]> updates = new ArrayList<>(rowKeys.size());
                for (int i = 0; i < rowKeys.size(); i++) {
                    Object sampled = sampler.sample(fk.refTable(), fk.refColumn(), i, strategy);
                    updates.add(new Object[]{sampled, rowKeys.get(i)});
                }
                jt.batchUpdate(sql, updates);
                l.onLog(Severity.INFO, "循环表 " + tableName + "." + fk.columnName() + " 外键回填完成");
            }
        }
        return rowsByTable;
    }

    /** 构建列计划：过滤掉连生成器都无需生成的列（自增、可空不可生成列）。 */
    private ColumnPlan planColumns(TableMetadata meta, TableConfig cfg, GenerationEngineListener l) {
        List<ColumnMetadata> columns = new ArrayList<>(meta.columns().size());
        List<ValueGeneratorFactory.ResolvedGenerator> generators = new ArrayList<>();
        Map<String, Integer> indexByColumn = new HashMap<>();
        for (ColumnMetadata column : meta.columns()) {
            String userGenerator = null;
            Map<String, Object> userParams = null;
            if (cfg.columns() != null) {
                ColumnConfig cc = cfg.columns().get(column.columnName());
                if (cc != null) {
                    userGenerator = cc.generator();
                    userParams = cc.params();
                }
            }
            ValueGeneratorFactory.ResolvedGenerator resolved =
                    factory.resolve(column, meta, userGenerator, userParams, l);
            if (resolved == null) {
                continue; // 自增列或可空不可生成列
            }
            indexByColumn.put(column.columnName(), columns.size());
            columns.add(column);
            generators.add(resolved);
        }
        return new ColumnPlan(List.copyOf(columns), List.copyOf(generators),
                Map.copyOf(indexByColumn));
    }

    /** 生成单行并做唯一检测，冲突重试；连续冲突达阈值抛唯一冲突异常。 */
    private Object[] generateRow(TableMetadata meta, ColumnPlan plan, TableConfig cfg,
                                 long rowIndex, long totalRows, UniqueConstraintChecker checker,
                                 FkMode fkMode, DatabaseDialect dialect, ForeignKeySampler fkSampler,
                                 SamplingStrategy strategy) {
        int attempts = 0;
        int consecutiveFailures = 0;
        while (true) {
            Object[] row = new Object[plan.columns().size()];
            Map<String, Object> params = Map.of();
            for (int i = 0; i < plan.columns().size(); i++) {
                ColumnMetadata column = plan.columns().get(i);
                if (fkMode == FkMode.NULL && isForeignKeyColumn(meta, column.columnName())) {
                    row[i] = null;
                    continue;
                }
                ForeignKeyMetadata fk = findForeignKey(meta, column.columnName());
                if (fk != null && fkMode == FkMode.SAMPLE) {
                    Object sampled = fkSampler.sample(fk.refTable(), fk.refColumn(), rowIndex, strategy);
                    params = Map.of(ForeignKeyGenerator.FK_VALUE_PARAM, sampled);
                } else {
                    params = Map.of();
                }
                GenerationContext context = new GenerationContext(meta.tableName(), column, rowIndex,
                        totalRows, params, dialect);
                row[i] = plan.generators().get(i).generate(context);
            }
            if (checkUnique(meta, plan, row, checker)) {
                alignNameWithGender(plan, row);
                return row;
            }
            consecutiveFailures++;
            attempts++;
            if (consecutiveFailures >= LOW_CARDINALITY_FAIL_THRESHOLD) {
                throw new UniqueConstraintException(
                        "表 " + meta.tableName() + " 行 " + rowIndex + " 唯一冲突连续 " + consecutiveFailures
                                + " 次未收敛，疑似低基数列耗尽取值空间");
            }
            if (attempts >= MAX_ROW_RETRY) {
                throw new UniqueConstraintException(
                        "表 " + meta.tableName() + " 行 " + rowIndex + " 唯一冲突重试超过 " + MAX_ROW_RETRY + " 次");
            }
        }
    }

    /** 对每个唯一索引组做本地 LRU 检测；组内存在 NULL 时跳过（数据库允许 NULL 重复）。 */
    private boolean checkUnique(TableMetadata meta, ColumnPlan plan, Object[] row,
                                UniqueConstraintChecker checker) {
        for (UniqueIndexMetadata unique : meta.uniqueIndexes()) {
            StringBuilder key = new StringBuilder();
            boolean nullInGroup = false;
            for (String columnName : unique.columns()) {
                int idx = plan.columnIndex(columnName);
                Object value = idx < 0 ? null : row[idx];
                if (value == null) {
                    nullInGroup = true;
                    break;
                }
                key.append(value).append('\u0001');
            }
            if (nullInGroup) {
                continue;
            }
            if (checker.checkAndAdd(unique.indexName(), key.toString())) {
                return false;
            }
        }
        return true;
    }

    /**
     * name 与 gender 联动校正（数据质量整改）：当行内存在 gender（枚举含 男/女）与 name 列时，
     * 若 name 的性别倾向与 gender 不一致，则按 gender 从对应语料名库重生成。
     * 外部/自定义姓名无法识别性别倾向（genderOfName 返回 null）时保持原值，不做强制改写。
     */
    private static void alignNameWithGender(ColumnPlan plan, Object[] row) {
        int genderIdx = -1;
        int nameIdx = -1;
        for (int i = 0; i < plan.columns().size(); i++) {
            ColumnMetadata c = plan.columns().get(i);
            String n = c.columnName().toLowerCase(Locale.ROOT);
            if (genderIdx < 0 && ("gender".equals(n) || "sex".equals(n)) && containsGenderEnum(c)) {
                genderIdx = i;
            }
            if (nameIdx < 0 && ("name".equals(n) || "full_name".equals(n))) {
                nameIdx = i;
            }
        }
        if (genderIdx < 0 || nameIdx < 0) {
            return;
        }
        String gender = String.valueOf(row[genderIdx]);
        if (!"男".equals(gender) && !"女".equals(gender)) {
            return;
        }
        String name = row[nameIdx] == null ? null : String.valueOf(row[nameIdx]);
        String nameGender = ChinesePersonData.genderOfName(name);
        if (nameGender != null && !nameGender.equals(gender)) {
            row[nameIdx] = ChinesePersonData.randomNameForGender(gender);
        }
    }

    /** 枚举值恰好包含 男/女 的列视为性别列（避免误伤其他自定义枚举）。 */
    private static boolean containsGenderEnum(ColumnMetadata column) {
        List<String> enums = column.enumValues();
        return enums != null && enums.contains("男") && enums.contains("女");
    }

    private void checkCancelled(GenerationEngineListener l) {
        if (l.isCancelled()) {
            throw new RelivusException(ErrorCode.TASK_CANCEL_FAILED, "任务已取消");
        }
    }

    private static boolean isForeignKeyColumn(TableMetadata meta, String columnName) {
        return findForeignKey(meta, columnName) != null;
    }

    private static ForeignKeyMetadata findForeignKey(TableMetadata meta, String columnName) {
        for (ForeignKeyMetadata fk : meta.foreignKeys()) {
            if (fk.columnName().equals(columnName)) {
                return fk;
            }
        }
        return null;
    }

    /** 无操作监听器（未提供监听器时阻塞更高层驱动取消）。 */
    private static final class NoopListener implements GenerationEngineListener {
        @Override
        public void onProgress(String table, long insertedRows, long totalRows) {
        }

        @Override
        public void onLog(Severity severity, String message) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}