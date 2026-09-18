package com.relivus.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dialect.DatabaseDialect;
import com.relivus.dto.GenerationConfig;
import com.relivus.dto.TaskDataPage;
import com.relivus.dto.TaskGeneratedTable;
import com.relivus.entity.TaskEntity;
import com.relivus.schema.SchemaIntrospector;
import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.TableMetadata;
import com.relivus.service.ConnectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 生成数据回看服务（DOC-06 / GET /api/tasks/{id}/tables、/data）。
 *
 * <p>原理：生成任务执行前采集各表基线 {@code MAX(主键)}（truncate 模式基线为 0，
 * 无数值主键的表基线为 null），随任务持久化至 {@code df_task.data_baseline_json}；
 * 任务成功后按「主键 &gt; 基线」精确回看本次生成的行，支持分页与表切换。
 * 表名仅接受任务配置白名单内的值，主键列名来自数据库元数据，过滤值全部走参数绑定。
 */
@Service
public class TaskDataService {

    private static final Logger LOG = LoggerFactory.getLogger(TaskDataService.class);

    /** 单页行数上限（前端分页与 SQL 分页一致，防超量拉取）。 */
    public static final int MAX_LIMIT = 200;

    private final ConnectionService connectionService;
    private final SchemaIntrospector introspector;
    private final ObjectMapper objectMapper;

    public TaskDataService(ConnectionService connectionService, SchemaIntrospector introspector,
                           ObjectMapper objectMapper) {
        this.connectionService = connectionService;
        this.introspector = introspector;
        this.objectMapper = objectMapper;
    }

    /**
     * 采集生成前各表基线：表 → 主键最大值（truncate 模式统一为 0，表示回看全表）。
     *
     * <p>无数值主键的表写入 null（回看时展示全表数据）。必须在引擎真正插入前调用。
     */
    public Map<String, Long> captureBaselines(DataSource dataSource, DatabaseDialect dialect,
                                              GenerationConfig config) {
        Map<String, Long> baselines = new LinkedHashMap<>();
        if (config.truncateBeforeOrDefault()) {
            for (GenerationConfig.TableConfig tc : config.tables()) {
                baselines.put(tc.table(), 0L);
            }
            return baselines;
        }
        JdbcTemplate jt = new JdbcTemplate(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            Map<String, TableMetadata> byName = new LinkedHashMap<>();
            for (TableMetadata table : introspector.introspect(connection, dialect)) {
                byName.put(table.tableName(), table);
            }
            for (GenerationConfig.TableConfig tc : config.tables()) {
                TableMetadata meta = byName.get(tc.table());
                ColumnMetadata pk = meta == null ? null : meta.primaryKeyColumn();
                if (pk != null && isNumericType(pk.dataType())) {
                    String sql = "SELECT COALESCE(MAX(" + dialect.quoteIdentifier(pk.columnName()) + "), 0) FROM "
                            + tc.table();
                    Long max = jt.queryForObject(sql, Long.class);
                    baselines.put(tc.table(), max == null ? 0L : max);
                } else {
                    baselines.put(tc.table(), null);
                }
            }
            return baselines;
        } catch (SQLException e) {
            throw new RelivusException(ErrorCode.SCHEMA_INTROSPECTION_FAILED,
                    "基线采集失败（目标库内省异常）：" + e.getMessage(), e);
        }
    }

    /** 生成任务表清单（前端表切换），含是否可按基线精确筛选。 */
    public List<TaskGeneratedTable> listTables(TaskEntity task) {
        requireSuccessfulGeneration(task);
        GenerationConfig config = parseConfig(task);
        Map<String, Long> baselines = parseBaselines(task);
        List<TaskGeneratedTable> result = new ArrayList<>();
        for (GenerationConfig.TableConfig tc : config.tables()) {
            result.add(new TaskGeneratedTable(tc.table(), tc.rowCount(),
                    baselines.containsKey(tc.table()) && baselines.get(tc.table()) != null));
        }
        return result;
    }

    /** 分页读取某表本次生成的数据。 */
    public TaskDataPage readData(TaskEntity task, String table, int limit, int offset) {
        requireSuccessfulGeneration(task);
        GenerationConfig config = parseConfig(task);
        if (config.tables().stream().noneMatch(tc -> tc.table().equals(table))) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                    "表 " + table + " 不在该任务生成范围内");
        }
        Map<String, Long> baselines = parseBaselines(task);
        Long baseline = baselines.get(table);

        DataSource dataSource = connectionService.resolveDataSource(task.getConnectionId());
        DatabaseDialect dialect = connectionService.dialect(task.getConnectionId());
        JdbcTemplate jt = new JdbcTemplate(dataSource);

        TableMetadata meta = introspectTable(dataSource, dialect, table);
        ColumnMetadata pk = meta.primaryKeyColumn();
        boolean watermarkApplied = baseline != null && pk != null && isNumericType(pk.dataType());

        String whereClause = watermarkApplied
                ? dialect.quoteIdentifier(pk.columnName()) + " > ?"
                : null;
        String fullSql = dialect.buildSelectSql(table, null, whereClause) + buildOrderBy(meta, dialect);
        String pagedSql = dialect.getPaginationSyntax().apply(fullSql, offset, limit);

        long total = countRows(jt, table, whereClause, baseline, watermarkApplied);
        List<TaskDataPage.TaskDataColumn> columns = readColumns(jt, dialect, table);
        List<List<Object>> rows = readRows(jt, pagedSql, columns.size(), baseline, watermarkApplied);

        return new TaskDataPage(table, columns, rows, total, limit, offset, watermarkApplied);
    }

    private void requireSuccessfulGeneration(TaskEntity task) {
        if (!"GENERATION".equals(task.getTaskType())) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                    "仅生成任务支持数据回看（当前类型：" + task.getTaskType() + "）");
        }
        if (task.getStatus() != TaskStatus.SUCCESS) {
            throw new RelivusException(ErrorCode.TASK_DATA_UNAVAILABLE,
                    "任务未成功完成（当前状态：" + task.getStatus() + "），无法查看生成数据");
        }
    }

    private TableMetadata introspectTable(DataSource dataSource, DatabaseDialect dialect, String table) {
        try (Connection connection = dataSource.getConnection()) {
            TableMetadata meta = introspector.introspectTable(connection, dialect, table);
            if (meta == null) {
                throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                        "表 " + table + " 已不存在于目标库");
            }
            return meta;
        } catch (RelivusException e) {
            throw e;
        } catch (SQLException e) {
            throw new RelivusException(ErrorCode.CONNECTION_FAILED,
                    "目标库连接失败：" + e.getMessage(), e);
        }
    }

    private long countRows(JdbcTemplate jt, String table, String whereClause,
                           Long baseline, boolean watermarkApplied) {
        String sql = "SELECT COUNT(*) FROM " + table
                + (whereClause == null ? "" : " WHERE " + whereClause);
        if (watermarkApplied) {
            return jt.queryForObject(sql, Long.class, baseline);
        }
        return jt.queryForObject(sql, Long.class);
    }

    private List<TaskDataPage.TaskDataColumn> readColumns(JdbcTemplate jt, DatabaseDialect dialect, String table) {
        String metaSql = dialect.buildSelectSql(table, null, "1=0");
        return jt.query(metaSql, rs -> {
            ResultSetMetaData md = rs.getMetaData();
            List<TaskDataPage.TaskDataColumn> columns = new ArrayList<>(md.getColumnCount());
            for (int i = 1; i <= md.getColumnCount(); i++) {
                columns.add(new TaskDataPage.TaskDataColumn(md.getColumnLabel(i), md.getColumnTypeName(i)));
            }
            return columns;
        });
    }

    private List<List<Object>> readRows(JdbcTemplate jt, String pagedSql, int columnCount,
                                        Long baseline, boolean watermarkApplied) {
        if (watermarkApplied) {
            return jt.query(pagedSql, (rs, rowNum) -> readRow(rs, columnCount), baseline);
        }
        return jt.query(pagedSql, (rs, rowNum) -> readRow(rs, columnCount));
    }

    private List<Object> readRow(java.sql.ResultSet rs, int columnCount) throws SQLException {
        List<Object> row = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            row.add(normalizeValue(rs.getObject(i)));
        }
        return row;
    }

    /** 归一化列值为 JSON 可序列化标量：数字/布尔/字符串原样，时间转 ISO 文本，字节转 hex，PG 对象取文本，数组转列表。 */
    static Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof Temporal) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            return "0x" + HexFormat.of().formatHex(bytes);
        }
        if (isPgObject(value)) {
            // PG jsonb/json 等对象：取其文本值（postgresql 驱动为 runtime 依赖，经反射避免编译期耦合）
            try {
                Object text = value.getClass().getMethod("getValue").invoke(value);
                return text == null ? null : String.valueOf(text);
            } catch (ReflectiveOperationException e) {
                return value.toString();
            }
        }
        if (value instanceof java.sql.Array array) {
            try {
                Object elements = array.getArray();
                if (elements != null && elements.getClass().isArray()) {
                    int length = Array.getLength(elements);
                    List<Object> list = new ArrayList<>(length);
                    for (int i = 0; i < length; i++) {
                        list.add(normalizeValue(Array.get(elements, i)));
                    }
                    return list;
                }
                return String.valueOf(elements);
            } catch (SQLException e) {
                return value.toString();
            }
        }
        return String.valueOf(value);
    }

    /** 是否数值类型主键（可做 {@code pk > 基线} 精确筛选）。 */
    static boolean isNumericType(String jdbcType) {
        if (jdbcType == null) {
            return false;
        }
        return switch (jdbcType.toUpperCase(Locale.ROOT)) {
            case "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT", "MEDIUMINT",
                 "INT2", "INT4", "INT8", "SERIAL", "BIGSERIAL",
                 "NUMERIC", "DECIMAL", "FLOAT", "FLOAT4", "FLOAT8", "DOUBLE", "REAL" -> true;
            default -> false;
        };
    }

    /** PG 复合对象（jsonb/json 等）识别：按类名匹配，避免编译期耦合 runtime 驱动。 */
    private static boolean isPgObject(Object value) {
        return "org.postgresql.util.PGobject".equals(value.getClass().getName());
    }

    private static String buildOrderBy(TableMetadata meta, DatabaseDialect dialect) {
        ColumnMetadata pk = meta.primaryKeyColumn();
        if (pk != null) {
            return " ORDER BY " + dialect.quoteIdentifier(pk.columnName());
        }
        if (!meta.columns().isEmpty()) {
            return " ORDER BY " + dialect.quoteIdentifier(meta.columns().get(0).columnName());
        }
        return "";
    }

    private GenerationConfig parseConfig(TaskEntity task) {
        try {
            return objectMapper.readValue(task.getConfigJson(), GenerationConfig.class);
        } catch (JsonProcessingException e) {
            throw new RelivusException(ErrorCode.TASK_DATA_UNAVAILABLE,
                    "任务配置解析失败，无法回看数据", e);
        }
    }

    private Map<String, Long> parseBaselines(TaskEntity task) {
        String json = task.getDataBaselineJson();
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Long>>() { });
        } catch (JsonProcessingException e) {
            LOG.warn("data_baseline_json 解析失败，按无数值主键处理, taskId={}", task.getId());
            return Map.of();
        }
    }
}
