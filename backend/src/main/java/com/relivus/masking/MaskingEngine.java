package com.relivus.masking;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dialect.DatabaseDialect;
import com.relivus.dto.MaskingConfig;
import com.relivus.dto.MaskingPreviewResponse;
import com.relivus.dto.MaskingPreviewResponse.PreviewRow;
import com.relivus.dto.MaskingTaskRequest;
import com.relivus.dto.MaskingTaskRequest.TableRule;
import com.relivus.dto.MaskingTaskRequest.TableRule.ColumnRule;
import com.relivus.masking.MaskingListener.Severity;
import com.relivus.schema.SchemaIntrospector;
import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.TableMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 脱敏引擎。
 *
 * <p>流程：内省目标表 → 解析列算法（用户配置 > 列名启发式 > 固定掩码）→ 关联列分组
 * → keyset/OFFSET 分页读取 → {@link GlobalMaskingContext} 逐值脱敏（保证跨表同值一致）
 * → 批量 UPDATE 回写。仅处理字符串类列，数字/时间列原值保留（避免类型转换破坏数据）。
 */
@Component
public class MaskingEngine {

    private static final Logger LOG = LoggerFactory.getLogger(MaskingEngine.class);

    private static final String DEFAULT_FIXED_MASK = "******";
    private static final int PREVIEW_ROW_LIMIT = 20;

    private final SchemaIntrospector introspector;
    private final GlobalMaskingContext context;
    private final RelatedColumnDetector detector = new RelatedColumnDetector();

    public MaskingEngine(SchemaIntrospector introspector, GlobalMaskingContext context) {
        this.introspector = introspector;
        this.context = context;
    }

    /** 单列解析产物：算法、分组、密钥版本。 */
    private record ColumnPlan(ColumnMetadata column, MaskingConfig config) {
    }

    /**
     * 执行脱敏。
     *
     * @param dataSource 目标库数据源
     * @param dialect    目标库方言
     * @param request    脱敏配置
     * @param listener   进度/日志/取消回调
     */
    public MaskingRunResult execute(DataSource dataSource, DatabaseDialect dialect,
                                    MaskingTaskRequest request, MaskingListener listener) {
        MaskingListener l = listener == null ? new NoopListener() : listener;
        long start = System.currentTimeMillis();
        JdbcTemplate jt = new JdbcTemplate(dataSource);

        List<TableMetadata> allTables = introspect(dataSource, dialect);
        Map<String, TableMetadata> byName = new LinkedHashMap<>();
        for (TableMetadata table : allTables) {
            byName.put(table.tableName(), table);
        }
        Map<String, List<RelatedColumnDetector.ColumnRef>> groups = detector.detect(allTables);

        Map<String, Long> rowsByTable = new LinkedHashMap<>();
        int batchSize = request.batchSizeOrDefault();
        for (TableRule rule : request.tables()) {
            TableMetadata meta = byName.get(rule.table());
            if (meta == null) {
                throw new RelivusException(ErrorCode.VALIDATION_FAILED, "表不存在：" + rule.table());
            }
            long processed = maskTable(jt, dialect, meta, rule, groups, l, batchSize);
            rowsByTable.put(rule.table(), processed);
            l.onLog(Severity.INFO, "表 " + rule.table() + " 脱敏完成，处理 " + processed + " 行");
        }
        return new MaskingRunResult(rowsByTable, System.currentTimeMillis() - start);
    }

    /**
     * 预览脱敏效果（不落库、不写映射）。
     *
     * @param limit 每列预览行数上限
     */
    public MaskingPreviewResponse preview(DataSource dataSource, DatabaseDialect dialect,
                                          MaskingTaskRequest request, int limit) {
        JdbcTemplate jt = new JdbcTemplate(dataSource);
        int previewLimit = limit > 0 ? limit : PREVIEW_ROW_LIMIT;
        List<PreviewRow> rows = new ArrayList<>();
        for (TableRule rule : request.tables()) {
            List<TableMetadata> allTables = introspect(dataSource, dialect);
            TableMetadata meta = allTables.stream()
                    .filter(t -> t.tableName().equals(rule.table())).findFirst().orElse(null);
            if (meta == null) {
                throw new RelivusException(ErrorCode.VALIDATION_FAILED, "表不存在：" + rule.table());
            }
            for (ColumnPlan plan : buildColumnPlans(meta, rule, Map.of())) {
                ColumnMetadata column = plan.column();
                String pkCol = meta.primaryKey();
                String base = dialect.buildSelectSql(rule.table(),
                        pkCol == null ? List.of(column.columnName()) : List.of(pkCol, column.columnName()),
                        rule.where());
                String sql = dialect.getPaginationSyntax().apply(base, 0, previewLimit);
                for (Map<String, Object> record : jt.queryForList(sql)) {
                    Object value = record.get(column.columnName());
                    if (value == null) {
                        continue;
                    }
                    String original = String.valueOf(value);
                    rows.add(new PreviewRow(rule.table(), column.columnName(), original,
                            maskValue(plan.config(), original)));
                }
            }
        }
        return new MaskingPreviewResponse(rows);
    }

    /** 单表脱敏主流程。 */
    private long maskTable(JdbcTemplate jt, DatabaseDialect dialect, TableMetadata meta,
                           TableRule rule, Map<String, List<RelatedColumnDetector.ColumnRef>> groups,
                           MaskingListener l, int batchSize) {
        List<ColumnPlan> plans = buildColumnPlans(meta, rule, groups);
        if (plans.isEmpty()) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                    "表 " + rule.table() + " 未配置任何可脱敏列（字符串类）");
        }
        long total = countRows(jt, dialect, meta, rule.where());
        ColumnMetadata pk = meta.primaryKeyColumn();
        if (pk == null) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                    "表 " + meta.tableName() + " 无主键，无法定位更新行");
        }
        long processed = 0;

        List<Object[]> updates = new ArrayList<>(Math.min(batchSize, 1024));
        Object lastPk = null;
        while (processed < total) {
            checkCancelled(l);
            LastKey lastKey = new LastKey();
            List<Map<String, Object>> records = fetchBatch(jt, dialect, meta, plans, rule.where(),
                    pk, lastPk, batchSize, lastKey);
            if (records.isEmpty()) {
                break;
            }
            for (Map<String, Object> record : records) {
                Object pkValue = record.get(pk.columnName());
                Object[] binding = new Object[plans.size() + 1];
                boolean changed = false;
                for (int i = 0; i < plans.size(); i++) {
                    ColumnPlan plan = plans.get(i);
                    Object value = record.get(plan.column().columnName());
                    String masked = maskValue(plan.config(), value, true);
                    binding[i] = masked;
                    changed = changed || (value != null && masked != null && !masked.equals(String.valueOf(value)));
                }
                if (changed) {
                    binding[plans.size()] = pkValue;
                    updates.add(binding);
                }
            }
            flushUpdates(jt, dialect, meta, plans, updates);
            processed += records.size();
            lastPk = lastKey.getValue();
            updates.clear();
            l.onProgress(meta.tableName(), processed, total);
        }
        return processed;
    }

    /** 游标结果封装（keyset 分页尾部键）。 */
    private static final class LastKey {
        private Object value;

        Object getValue() {
            return value;
        }

        void set(Object value) {
            this.value = value;
        }
    }

    /** 分页读取一批记录：有单列主键走 keyset，否则走 OFFSET。 */
    private List<Map<String, Object>> fetchBatch(JdbcTemplate jt, DatabaseDialect dialect, TableMetadata meta,
                                                 List<ColumnPlan> plans, String where,
                                                 ColumnMetadata pk, Object lastPk, int batchSize, LastKey lastKey) {
        List<String> columns = new ArrayList<>();
        if (pk != null) {
            columns.add(pk.columnName());
        }
        for (ColumnPlan plan : plans) {
            if (!columns.contains(plan.column().columnName())) {
                columns.add(plan.column().columnName());
            }
        }
        String whereClause = where;
        Object[] args = new Object[0];
        if (pk != null && lastPk != null) {
            whereClause = (where == null || where.isBlank() ? "1=1" : where)
                    + " AND " + dialect.quoteIdentifier(pk.columnName()) + " > ?";
            args = new Object[]{lastPk};
        }
        String base = dialect.buildSelectSql(meta.tableName(), columns, whereClause)
                + " ORDER BY " + dialect.quoteIdentifier(pk == null ? meta.tableName() : pk.columnName());
        String sql = dialect.getPaginationSyntax().apply(base, 0, batchSize);
        List<Map<String, Object>> records;
        if (args.length > 0) {
            records = jt.queryForList(sql, args);
        } else {
            records = jt.queryForList(sql);
        }
        if (pk != null && !records.isEmpty()) {
            lastKey.set(records.get(records.size() - 1).get(pk.columnName()));
        }
        return records;
    }

    /** 构建列计划：跳过不存在的列与已配置算法为空且无启发式命中的列。 */
    private List<ColumnPlan> buildColumnPlans(TableMetadata meta, TableRule rule,
                                              Map<String, List<RelatedColumnDetector.ColumnRef>> groups) {
        List<ColumnPlan> plans = new ArrayList<>();
        for (Map.Entry<String, ColumnRule> entry : rule.columns().entrySet()) {
            ColumnMetadata column = meta.column(entry.getKey());
            if (column == null) {
                throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                        "表 " + rule.table() + " 不存在列：" + entry.getKey());
            }
            if (!isStringColumn(column)) {
                continue; // 数字/时间列原值保留
            }
            ColumnRule cr = entry.getValue();
            String algorithm = cr.algorithm();
            if (algorithm == null || algorithm.isBlank()) {
                algorithm = heuristicAlgorithm(column.columnName());
            }
            if (algorithm == null) {
                LOG.info("masking skip column {} (no algorithm configured)", column.columnName());
                continue;
            }
            String group = cr.columnGroup();
            if (group == null || group.isBlank()) {
                String fkGroup = RelatedColumnDetector.findForeignKeyGroup(groups, meta.tableName(), column.columnName());
                group = fkGroup != null ? fkGroup : meta.tableName() + "." + column.columnName();
            }
            Map<String, String> params = cr.params() == null ? Map.of() : cr.params();
            plans.add(new ColumnPlan(column,
                    new MaskingConfig(algorithm, params, group, cr.keyVersionOrDefault())));
        }
        return plans;
    }

    /** 脱敏单个值：null 原样返回；仅字符串可脱敏。预览路径（不落库、不写映射）。 */
    private String maskValue(MaskingConfig config, Object value) {
        return maskValue(config, value, false);
    }

    /**
     * 脱敏单个值。
     *
     * @param persist true 走 {@link GlobalMaskingContext#mask}（同组跨表一致 + 持久化映射），
     *                false 直接调用算法（预览，不污染映射表）
     */
    private String maskValue(MaskingConfig config, Object value, boolean persist) {
        if (value == null) {
            return null;
        }
        String original = String.valueOf(value);
        MaskingAlgorithm algorithm = context.resolveAlgorithm(config.algorithm());
        if (algorithm == null) {
            throw new RelivusException(ErrorCode.MASKING_FAILED, "未知脱敏算法 '" + config.algorithm() + "'");
        }
        if (!persist) {
            return algorithm.mask(original, config);
        }
        return context.mask(config.columnGroup(), original, config);
    }

    private long countRows(JdbcTemplate jt, DatabaseDialect dialect, TableMetadata meta, String where) {
        String sql = "SELECT COUNT(*) FROM " + dialect.quoteIdentifier(meta.tableName());
        if (where != null && !where.isBlank()) {
            sql += " WHERE " + where;
        }
        Long count = jt.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private void flushUpdates(JdbcTemplate jt, DatabaseDialect dialect, TableMetadata meta,
                              List<ColumnPlan> plans, List<Object[]> updates) {
        if (updates.isEmpty()) {
            return;
        }
        ColumnMetadata pk = meta.primaryKeyColumn();
        List<String> setColumns = new ArrayList<>();
        for (ColumnPlan plan : plans) {
            setColumns.add(plan.column().columnName());
        }
        String sql = dialect.buildUpdateSql(meta.tableName(), setColumns,
                dialect.quoteIdentifier(pk.columnName()) + " = ?");
        int[] counts = jt.batchUpdate(sql, updates);
        if (LOG.isDebugEnabled()) {
            LOG.debug("mask update table={}, batch={}, updated={}", meta.tableName(), updates.size(),
                    java.util.Arrays.stream(counts).sum());
        }
    }

    private List<TableMetadata> introspect(DataSource dataSource, DatabaseDialect dialect) {
        try (Connection connection = dataSource.getConnection()) {
            return introspector.introspect(connection, dialect);
        } catch (SQLException e) {
            throw new RelivusException(ErrorCode.CONNECTION_FAILED, "目标库连接失败：" + e.getMessage(), e);
        }
    }

    private static boolean isStringColumn(ColumnMetadata column) {
        String t = column.dataType().toUpperCase(Locale.ROOT);
        return t.contains("CHAR") || t.contains("TEXT");
    }

    /** 列名启发式算法选择（未命中返回 null）。 */
    static String heuristicAlgorithm(String columnName) {
        if (columnName == null) {
            return null;
        }
        String n = columnName.toLowerCase(Locale.ROOT);
        if (n.contains("phone") || n.contains("mobile") || n.contains("tel")) {
            return "phone";
        }
        if (n.contains("id_card") || n.contains("identity_card") || n.contains("identity_no")
                || n.contains("idcard") || n.contains("identify")) {
            return "id_card";
        }
        if (n.contains("bank_card") || n.contains("bankcard") || n.contains("card_no")
                || n.contains("account_no")) {
            return "bank_card";
        }
        if (n.contains("email") || n.contains("mail")) {
            return "faker";
        }
        if (n.contains("name") || n.contains("real_name") || n.contains("nickname")
                || n.contains("full_name") || n.contains("username")) {
            return "faker";
        }
        if (n.contains("address") || n.contains("addr") || n.contains("street")) {
            return "faker";
        }
        return null;
    }

    private static void checkCancelled(MaskingListener l) {
        if (l.isCancelled()) {
            throw new RelivusException(ErrorCode.TASK_CANCEL_FAILED, "任务已取消");
        }
    }

    /** 无操作监听器。 */
    private static final class NoopListener implements MaskingListener {
        @Override
        public void onProgress(String table, long processedRows, long totalRows) {
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