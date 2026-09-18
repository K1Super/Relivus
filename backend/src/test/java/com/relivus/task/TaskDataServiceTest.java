package com.relivus.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dialect.DatabaseDialect;
import com.relivus.dialect.MySQLDialect;
import com.relivus.dto.GenerationConfig;
import com.relivus.dto.GenerationConfig.TableConfig;
import com.relivus.dto.TaskDataPage;
import com.relivus.dto.TaskGeneratedTable;
import com.relivus.entity.TaskEntity;
import com.relivus.schema.SchemaIntrospector;
import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.TableMetadata;
import com.relivus.service.ConnectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 生成数据回看服务测试（DOC-06）。
 *
 * <p>基线采集与分页查数使用 H2（MODE=MySQL）走真实 SQL 路径；表元数据经
 * {@link SchemaIntrospector} mock 注入，隔离目标库内省差异。
 */
class TaskDataServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DataSource H2 = new DriverManagerDataSource(
            "jdbc:h2:mem:taskdata;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");

    private ConnectionService connectionService;
    private SchemaIntrospector introspector;
    private TaskDataService service;

    @BeforeEach
    void setUp() throws Exception {
        connectionService = mock(ConnectionService.class);
        introspector = mock(SchemaIntrospector.class);
        service = new TaskDataService(connectionService, introspector, MAPPER);
        when(connectionService.resolveDataSource(1L)).thenReturn(H2);
        when(connectionService.dialect(1L)).thenReturn(new MySQLDialect());
        createUsersTable();
    }

    // ---- 基线采集 ----

    @Test
    void captureBaselinesTruncateModeReturnsZeroWithoutIntrospection() throws Exception {
        GenerationConfig config = new GenerationConfig(1L,
                List.of(new TableConfig("users", 10, Map.of()), new TableConfig("orders", 20, Map.of())),
                "UNIFORM", true, 100);

        Map<String, Long> baselines = service.captureBaselines(H2, new MySQLDialect(), config);

        assertThat(baselines).containsEntry("users", 0L).containsEntry("orders", 0L);
        verify(introspector, never()).introspect(any(Connection.class), any(DatabaseDialect.class));
    }

    @Test
    void captureBaselinesAppendModeCapturesMaxPk() throws Exception {
        seedUsers(3);
        when(introspector.introspect(any(Connection.class), any(DatabaseDialect.class)))
                .thenReturn(List.of(usersMeta()));
        GenerationConfig config = new GenerationConfig(1L,
                List.of(new TableConfig("users", 10, Map.of())), "UNIFORM", false, 100);

        Map<String, Long> baselines = service.captureBaselines(H2, new MySQLDialect(), config);

        assertThat(baselines).containsEntry("users", 3L);
    }

    @Test
    void captureBaselinesNonNumericPkStoresNull() throws Exception {
        when(introspector.introspect(any(Connection.class), any(DatabaseDialect.class)))
                .thenReturn(List.of(usersMetaWithUuidPk()));
        GenerationConfig config = new GenerationConfig(1L,
                List.of(new TableConfig("users", 10, Map.of())), "UNIFORM", false, 100);

        Map<String, Long> baselines = service.captureBaselines(H2, new MySQLDialect(), config);

        assertThat(baselines).containsEntry("users", null);
    }

    // ---- 表清单 ----

    @Test
    void listTablesParsesConfigAndBaselineFlags() {
        TaskEntity task = generationSuccessTask(configJson("users", "orders"), "{\"users\":5,\"orders\":null}");

        List<TaskGeneratedTable> tables = service.listTables(task);

        assertThat(tables).hasSize(2);
        assertThat(tables.get(0)).isEqualTo(new TaskGeneratedTable("users", 10, true));
        assertThat(tables.get(1)).isEqualTo(new TaskGeneratedTable("orders", 10, false));
    }

    // ---- 分页数据 ----

    @Test
    void readDataReturnsOnlyGeneratedRowsWithWatermark() throws Exception {
        seedUsers(8);
        when(introspector.introspectTable(any(Connection.class), any(DatabaseDialect.class), any()))
                .thenReturn(usersMeta());
        TaskEntity task = generationSuccessTask(configJson("users"), "{\"users\":5}");

        TaskDataPage page = service.readData(task, "users", 100, 0);

        assertThat(page.watermarkApplied()).isTrue();
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.rows()).hasSize(3);
        assertThat(page.rows().get(0).get(0)).isEqualTo(6L);
        assertThat(page.rows().get(2).get(0)).isEqualTo(8L);
        assertThat(page.columns()).extracting(TaskDataPage.TaskDataColumn::name)
                .containsExactlyInAnyOrder("ID", "NAME", "CREATED_AT"); // H2 元数据列名大写
    }

    @Test
    void readDataAppliesOffsetAndLimit() throws Exception {
        seedUsers(8);
        when(introspector.introspectTable(any(Connection.class), any(DatabaseDialect.class), any()))
                .thenReturn(usersMeta());
        TaskEntity task = generationSuccessTask(configJson("users"), "{\"users\":5}");

        TaskDataPage page = service.readData(task, "users", 2, 1);

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.rows()).hasSize(2);
        assertThat(page.rows().get(0).get(0)).isEqualTo(7L);
        assertThat(page.rows().get(1).get(0)).isEqualTo(8L);
    }

    @Test
    void readDataRejectsTaskNotSuccess() {
        TaskEntity task = generationSuccessTask(configJson("users"), "{\"users\":5}");
        task.setStatus(TaskStatus.RUNNING);

        assertThatThrownBy(() -> service.readData(task, "users", 100, 0))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.TASK_DATA_UNAVAILABLE.getCode());
    }

    @Test
    void readDataRejectsTableOutsideConfig() {
        TaskEntity task = generationSuccessTask(configJson("users"), "{\"users\":5}");

        assertThatThrownBy(() -> service.readData(task, "orders", 100, 0))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED.getCode());
    }

    @Test
    void readDataRejectsNonGenerationTask() {
        TaskEntity task = generationSuccessTask(configJson("users"), "{\"users\":5}");
        task.setTaskType("MASKING");

        assertThatThrownBy(() -> service.readData(task, "users", 100, 0))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED.getCode());
    }

    // ---- 值归一化 ----

    @Test
    void normalizeValueHandlesCommonJdbcTypes() throws Exception {
        assertThat(TaskDataService.normalizeValue(null)).isNull();
        assertThat(TaskDataService.normalizeValue(42)).isEqualTo(42);
        assertThat(TaskDataService.normalizeValue(true)).isEqualTo(true);
        assertThat(TaskDataService.normalizeValue("abc")).isEqualTo("abc");
        assertThat(TaskDataService.normalizeValue(Timestamp.valueOf("2024-01-02 03:04:05")))
                .isEqualTo("2024-01-02T03:04:05");
        assertThat(TaskDataService.normalizeValue(new byte[]{1, 2, 3})).isEqualTo("0x010203");

        org.postgresql.util.PGobject pg = new org.postgresql.util.PGobject();
        pg.setType("jsonb");
        pg.setValue("{\"a\":1}");
        assertThat(TaskDataService.normalizeValue(pg)).isEqualTo("{\"a\":1}");

        try (Connection connection = H2.getConnection()) {
            java.sql.Array array = connection.createArrayOf("BIGINT", new Long[]{1L, 2L, 3L});
            assertThat(TaskDataService.normalizeValue(array)).isEqualTo(List.of(1L, 2L, 3L));
        }
    }

    @Test
    void isNumericTypeRecognizesCommonPkTypes() {
        assertThat(TaskDataService.isNumericType("BIGINT")).isTrue();
        assertThat(TaskDataService.isNumericType("int4")).isTrue();
        assertThat(TaskDataService.isNumericType("UUID")).isFalse();
        assertThat(TaskDataService.isNumericType("VARCHAR")).isFalse();
        assertThat(TaskDataService.isNumericType(null)).isFalse();
    }

    // ---- helpers ----

    private static void createUsersTable() throws SQLException {
        new JdbcTemplate(H2).execute(
                "DROP TABLE IF EXISTS users");
        new JdbcTemplate(H2).execute(
                "CREATE TABLE users (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                        + " name VARCHAR(64), created_at TIMESTAMP)");
    }

    private static void seedUsers(int count) {
        JdbcTemplate jt = new JdbcTemplate(H2);
        for (int i = 1; i <= count; i++) {
            jt.update("INSERT INTO users(name, created_at) VALUES (?, ?)",
                    "user-" + i, Timestamp.valueOf("2024-01-01 10:00:0" + (i % 10)));
        }
    }

    private static TableMetadata usersMeta() {
        return new TableMetadata("users",
                List.of(new ColumnMetadata("id", "BIGINT", false, null, true, List.of()),
                        new ColumnMetadata("name", "VARCHAR", true, null, false, List.of()),
                        new ColumnMetadata("created_at", "TIMESTAMP", true, null, false, List.of())),
                List.of(), List.of(), List.of(), "id");
    }

    private static TableMetadata usersMetaWithUuidPk() {
        return new TableMetadata("users",
                List.of(new ColumnMetadata("id", "UUID", false, null, false, List.of())),
                List.of(), List.of(), List.of(), "id");
    }

    private static TaskEntity generationSuccessTask(String configJson, String baselineJson) {
        TaskEntity task = new TaskEntity();
        task.setId(7L);
        task.setTaskType("GENERATION");
        task.setConnectionId(1L);
        task.setConfigJson(configJson);
        task.setDataBaselineJson(baselineJson);
        task.setStatus(TaskStatus.SUCCESS);
        return task;
    }

    private static String configJson(String... tables) {
        List<TableConfig> list = List.of(tables).stream()
                .map(t -> new TableConfig(t, 10, Map.of()))
                .toList();
        GenerationConfig config = new GenerationConfig(1L, list, "UNIFORM", false, 100);
        try {
            return MAPPER.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
