package com.relivus.generator;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dialect.MySQLDialect;
import com.relivus.dto.GenerationConfig;
import com.relivus.schema.SchemaIntrospector;
import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.ForeignKeyMetadata;
import com.relivus.schema.model.TableMetadata;
import com.relivus.schema.model.UniqueIndexMetadata;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据生成引擎测试（行覆盖）。
 *
 * <p>introspector 以 mock 提供预置元数据，目标库用 H2（MODE=MySQL）驱动真实 SQL
 * 路径：依赖拓扑、FK 采样、批量插入、主键回填、唯一约束兜底、两阶段循环插入。
 */
class DataGenerationEngineTest {

    private static final MySQLDialect DIALECT = new MySQLDialect();

    private JdbcDataSource dataSource;
    private DataGenerationEngine engine;
    private SchemaIntrospector introspector;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:dgen;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS orders");
            s.execute("DROP TABLE IF EXISTS customers");
            s.execute("DROP TABLE IF EXISTS employees");
            s.execute("DROP TABLE IF EXISTS team_a CASCADE");
            s.execute("DROP TABLE IF EXISTS team_b CASCADE");
            s.execute("CREATE TABLE customers (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + " name VARCHAR(64) NOT NULL, email VARCHAR(128))");
            s.execute("CREATE UNIQUE INDEX uk_email ON customers(email)");
            s.execute("CREATE TABLE orders (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + " customer_id BIGINT NOT NULL, total DECIMAL(10,2),"
                    + " CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id))");
            // 循环引用：先建两表，再用 ALTER 补外键约束（H2 不允许建表时引用不存在的表）
            s.execute("CREATE TABLE team_b (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + " mid_id BIGINT NULL, name VARCHAR(64) NOT NULL)");
            s.execute("CREATE TABLE team_a (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + " lead_id BIGINT NULL, name VARCHAR(64) NOT NULL)");
            s.execute("ALTER TABLE team_a ADD CONSTRAINT fk_a_lead FOREIGN KEY (lead_id) REFERENCES team_b(id)");
            s.execute("ALTER TABLE team_b ADD CONSTRAINT fk_b_mid FOREIGN KEY (mid_id) REFERENCES team_a(id)");
        }
        introspector = mock(SchemaIntrospector.class);
        engine = new DataGenerationEngine(new ValueGeneratorFactory(), introspector);
    }

    @Test
    void generatesParentThenChildWithFkSampling() {
        when(introspector.introspect(any(), any())).thenReturn(List.of(ordersMeta(), customersMeta()));
        GenerationRunResult result = engine.execute(dataSource, DIALECT,
                cfg(List.of(table("customers", 5), table("orders", 10)), "UNIFORM", null), null);
        assertThat(result.rowsByTable()).containsEntry("customers", 5L).containsEntry("orders", 10L);
        Long valid = new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM orders WHERE customer_id IN (SELECT id FROM customers)", Long.class);
        assertThat(valid).isEqualTo(10L);
    }

    @Test
    void zipfStrategyStillProducesValidFk() {
        when(introspector.introspect(any(), any())).thenReturn(List.of(ordersMeta(), customersMeta()));
        GenerationRunResult result = engine.execute(dataSource, DIALECT,
                cfg(List.of(table("customers", 4), table("orders", 8)), "ZIPF", null), null);
        assertThat(result.rowsByTable()).containsEntry("customers", 4L).containsEntry("orders", 8L);
    }

    @Test
    void twoTableCycleUsesTwoPhaseInsert() {
        when(introspector.introspect(any(), any()))
                .thenReturn(List.of(teamBMeta(true), teamAMeta(true)));
        GenerationRunResult result = engine.execute(dataSource, DIALECT,
                cfg(List.of(table("team_a", 6), table("team_b", 6)), "UNIFORM", null), null);
        assertThat(result.rowsByTable()).containsEntry("team_a", 6L).containsEntry("team_b", 6L);
        Long nullsA = new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM team_a WHERE lead_id IS NULL", Long.class);
        Long nullsB = new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM team_b WHERE mid_id IS NULL", Long.class);
        assertThat(nullsA).isZero();
        assertThat(nullsB).isZero();
    }

    @Test
    void circularFkNotNullRejectedWith3003() throws Exception {
        when(introspector.introspect(any(), any()))
                .thenReturn(List.of(teamBMeta(true), teamAMeta(false)));
        DataSource bogus = mock(DataSource.class);
        when(bogus.getConnection()).thenReturn(mock(Connection.class));
        assertThatThrownBy(() -> engine.execute(bogus, DIALECT,
                cfg(List.of(table("team_a", 2), table("team_b", 2)), "UNIFORM", null), null))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CIRCULAR_FK_NOT_NULL.getCode());
    }

    @Test
    void truncateBeforeClearsExistingRows() {
        new JdbcTemplate(dataSource).update("INSERT INTO customers(name) VALUES ('a'), ('b')");
        when(introspector.introspect(any(), any())).thenReturn(List.of(customersMeta()));
        GenerationRunResult result = engine.execute(dataSource, DIALECT,
                cfg(List.of(table("customers", 3)), "UNIFORM", true), null);
        assertThat(result.rowsByTable()).containsEntry("customers", 3L);
        Long total = new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM customers", Long.class);
        assertThat(total).isEqualTo(3L);
    }

    @Test
    void cancelPropagatesTaskCancelFailed() throws Exception {
        when(introspector.introspect(any(), any())).thenReturn(List.of(customersMeta()));
        assertThatThrownBy(() -> engine.execute(dataSource, DIALECT,
                cfg(List.of(table("customers", 5)), "UNIFORM", null),
                new CancellingListener()))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.TASK_CANCEL_FAILED.getCode());
    }

    @Test
    void unknownTableRejectedWithValidation() {
        when(introspector.introspect(any(), any())).thenReturn(List.of(customersMeta()));
        assertThatThrownBy(() -> engine.execute(dataSource, DIALECT,
                cfg(List.of(table("nope", 1)), "UNIFORM", null), null))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED.getCode());
    }

    @Test
    void invalidSamplingStrategyRejected() {
        assertThatThrownBy(() -> engine.execute(dataSource, DIALECT,
                cfg(List.of(table("customers", 1)), "RANDOM", null), null))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED.getCode());
    }

    @Test
    void lowCardinalityUniqueConflictThrows() {
        when(introspector.introspect(any(), any())).thenReturn(List.of(customersMeta()));
        GenerationConfig cfgWithFixedEmail = new GenerationConfig(1L,
                List.of(new GenerationConfig.TableConfig("customers", 5,
                        Map.of("email", new GenerationConfig.TableConfig.ColumnConfig("fixed", Map.of("value", "x"))))),
                "UNIFORM", null, 1);
        assertThatThrownBy(() -> engine.execute(dataSource, DIALECT, cfgWithFixedEmail, null))
                .isInstanceOf(UniqueConstraintException.class)
                .hasMessageContaining("低基数");
    }

    @Test
    void dbUniqueConflictFallsBackToRowByRow() {
        // 元数据不含唯一索引（模拟本地检测失灵），数据库真实唯一索引兜底
        TableMetadata metaNoUk = new TableMetadata("customers",
                List.of(col("id", "BIGINT", false, true),
                        col("name", "VARCHAR", false, false),
                        col("email", "VARCHAR", true, false)),
                List.of(), List.of(), List.of(), "id");
        when(introspector.introspect(any(), any())).thenReturn(List.of(metaNoUk));
        GenerationConfig cfgWithFixedEmail = new GenerationConfig(1L,
                List.of(new GenerationConfig.TableConfig("customers", 3,
                        Map.of("email", new GenerationConfig.TableConfig.ColumnConfig("fixed", Map.of("value", "x"))))),
                "UNIFORM", null, 1);
        assertThatThrownBy(() -> engine.execute(dataSource, DIALECT, cfgWithFixedEmail, null))
                .isInstanceOf(UniqueConstraintException.class);
    }

    @Test
    void progressAndLogCallbacksFired() {
        RecordingListener listener = new RecordingListener();
        when(introspector.introspect(any(), any())).thenReturn(List.of(customersMeta()));
        engine.execute(dataSource, DIALECT, cfg(List.of(table("customers", 3)), "UNIFORM", null), listener);
        assertThat(listener.logs.stream().anyMatch(l -> l.contains("生成完成"))).isTrue();
        assertThat(listener.progress >= 1).isTrue();
    }

    @Test
    void nameAlignsWithGender() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("ALTER TABLE customers ADD COLUMN gender VARCHAR(8)");
        }
        when(introspector.introspect(any(), any())).thenReturn(List.of(customersGenderMeta()));
        GenerationRunResult result = engine.execute(dataSource, DIALECT,
                cfg(List.of(table("customers", 60)), "UNIFORM", null), null);
        assertThat(result.rowsByTable()).containsEntry("customers", 60L);
        List<Map<String, Object>> rows = new JdbcTemplate(dataSource).queryForList("SELECT name, gender FROM customers");
        assertThat(rows).hasSize(60);
        for (Map<String, Object> r : rows) {
            String name = String.valueOf(r.get("name"));
            String gender = String.valueOf(r.get("gender"));
            assertThat(ChinesePersonData.genderOfName(name))
                    .as("name=%s gender=%s", name, gender)
                    .isEqualTo(gender);
        }
    }

    // ---- 构造辅助 ----

    private static GenerationConfig cfg(List<GenerationConfig.TableConfig> tables, String strategy, Boolean truncate) {
        return new GenerationConfig(1L, tables, strategy, truncate, 1);
    }

    private static GenerationConfig.TableConfig table(String name, int rows) {
        return new GenerationConfig.TableConfig(name, rows, null);
    }

    private static ColumnMetadata col(String name, String type, boolean nullable, boolean autoInc) {
        return new ColumnMetadata(name, type, nullable, null, autoInc, List.of());
    }

    private static ColumnMetadata col(String name, String type, boolean nullable, boolean autoInc, List<String> enums) {
        return new ColumnMetadata(name, type, nullable, null, autoInc, enums);
    }

    private static TableMetadata customersGenderMeta() {
        return new TableMetadata("customers",
                List.of(col("id", "BIGINT", false, true),
                        col("name", "VARCHAR", false, false),
                        col("gender", "VARCHAR", false, false, List.of("男", "女"))),
                List.of(), List.of(), List.of(), "id");
    }

    private static TableMetadata customersMeta() {
        return new TableMetadata("customers",
                List.of(col("id", "BIGINT", false, true),
                        col("name", "VARCHAR", false, false),
                        col("email", "VARCHAR", true, false)),
                List.of(),
                List.of(new UniqueIndexMetadata("uk_email", List.of("email"))),
                List.of(), "id");
    }

    private static TableMetadata ordersMeta() {
        return new TableMetadata("orders",
                List.of(col("id", "BIGINT", false, true),
                        col("customer_id", "BIGINT", false, false),
                        col("total", "DECIMAL", true, false)),
                List.of(new ForeignKeyMetadata("fk_orders_customer", "customer_id", "customers", "id", false)),
                List.of(), List.of(), "id");
    }

    private static TableMetadata teamAMeta(boolean nullableLead) {
        return new TableMetadata("team_a",
                List.of(col("id", "BIGINT", false, true),
                        col("lead_id", "BIGINT", nullableLead, false),
                        col("name", "VARCHAR", false, false)),
                List.of(new ForeignKeyMetadata("fk_a_lead", "lead_id", "team_b", "id", nullableLead)),
                List.of(), List.of(), "id");
    }

    private static TableMetadata teamBMeta(boolean nullableMid) {
        return new TableMetadata("team_b",
                List.of(col("id", "BIGINT", false, true),
                        col("mid_id", "BIGINT", nullableMid, false),
                        col("name", "VARCHAR", false, false)),
                List.of(new ForeignKeyMetadata("fk_b_mid", "mid_id", "team_a", "id", nullableMid)),
                List.of(), List.of(), "id");
    }

    /** 取消监听器：首查即取消。 */
    private static final class CancellingListener implements GenerationEngineListener {
        @Override
        public void onProgress(String table, long insertedRows, long totalRows) {
        }

        @Override
        public void onLog(Severity severity, String message) {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    }

    /** 记录回调的监听器。 */
    private static final class RecordingListener implements GenerationEngineListener {
        private final java.util.List<String> logs = new java.util.ArrayList<>();
        private int progress;

        @Override
        public void onProgress(String table, long insertedRows, long totalRows) {
            progress++;
        }

        @Override
        public void onLog(Severity severity, String message) {
            logs.add(message);
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}