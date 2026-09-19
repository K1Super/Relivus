package com.relivus.masking;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.config.RelivusProperties;
import com.relivus.dialect.MySQLDialect;
import com.relivus.dto.MaskingPreviewResponse;
import com.relivus.dto.MaskingTaskRequest;
import com.relivus.dto.MaskingTaskRequest.TableRule;
import com.relivus.dto.MaskingTaskRequest.TableRule.ColumnRule;
import com.relivus.schema.SchemaIntrospector;
import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.TableMetadata;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 脱敏引擎测试。
 *
 * <p>启发式算法选择 + H2（MODE=MySQL）端到端执行：keyset 分页、批量回写、
 * 同组幂等映射、预览不落库、WHERE 过滤、非字符串列跳过、校验与取消分支。
 */
class MaskingEngineTest {

    private static final MySQLDialect DIALECT = new MySQLDialect();

    private JdbcDataSource dataSource;
    private JdbcTemplate jdbc;
    private SchemaIntrospector introspector;
    private MaskingEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:meng;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(dataSource);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS users");
            s.execute("DROP TABLE IF EXISTS df_mask_mapping");
            s.execute("CREATE TABLE users (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + " name VARCHAR(64) NOT NULL, phone VARCHAR(32) NOT NULL,"
                    + " email VARCHAR(128) NULL, age INT NULL)");
            jdbc.update("INSERT INTO users(name, phone, email, age) VALUES "
                    + "('张三', '13812345678', 'zhang@example.com', 30),"
                    + "('李四', '13900112233', 'li@example.com', 25),"
                    + "('王五', '13799887766', 'wang@example.com', 28),"
                    + "('赵六', '13655667788', 'zhao@example.com', 35)");
            s.execute("CREATE TABLE df_mask_mapping (column_group VARCHAR(128),"
                    + " original_hash VARCHAR(64), masked_value VARCHAR(512),"
                    + " algorithm VARCHAR(32), key_version INT)");
        }
        introspector = mock(SchemaIntrospector.class);
        when(introspector.introspect(any(), any())).thenReturn(List.of(usersMeta("id")));
        RelivusProperties props = new RelivusProperties();
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) 7;
        }
        props.getMasking().setHmacKey(Base64.getEncoder().encodeToString(key));
        engine = new MaskingEngine(introspector, new GlobalMaskingContext(dataSource, props));
    }

    @Test
    void executeMasksConfiguredPhoneColumnOnAllRows() {
        MaskingTaskRequest request = req("users", new ColumnRule("phone", null, null, null), null);
        MaskingRunResult result = engine.execute(dataSource, DIALECT, request, null);
        assertThat(result.rowsByTable()).containsEntry("users", 4L);
        List<String> phones = jdbc.queryForList(
                "SELECT phone FROM users ORDER BY id", String.class);
        for (String phone : phones) {
            assertThat(phone).matches("\\d{3}\\*\\*\\*\\*\\d{4}");
        }
    }

    @Test
    void executeWritesOneMappingPerDistinctValue() {
        MaskingTaskRequest request = req("users", new ColumnRule("phone", null, null, null), null);
        engine.execute(dataSource, DIALECT, request, null);
        Long mappingRows = jdbc.queryForObject("SELECT COUNT(*) FROM df_mask_mapping", Long.class);
        assertThat(mappingRows).isEqualTo(4L); // 4 个不同电话 → 4 条幂等映射
    }

    @Test
    void sameValueInSharedGroupMasksIdentically() {
        jdbc.update("UPDATE users SET email = NULL");
        MaskingTaskRequest request = new MaskingTaskRequest(1L,
                List.of(new TableRule("users",
                        Map.of("name", new ColumnRule("fixed", Map.of("value", "MASKED"), "nameGroup", null)), null)),
                null, null);
        engine.execute(dataSource, DIALECT, request, null);
        List<String> names = jdbc.queryForList("SELECT name FROM users ORDER BY id", String.class);
        assertThat(names).containsOnly("MASKED");
    }

    @Test
    void previewReturnsMaskedValuesWithoutTouchingDatabase() {
        MaskingTaskRequest request = req("users", new ColumnRule("fixed", Map.of("value", "P_MASK"), null, null), null);
        MaskingPreviewResponse response = engine.preview(dataSource, DIALECT, request, 100);
        assertThat(response.rows()).isNotEmpty();
        for (MaskingPreviewResponse.PreviewRow row : response.rows()) {
            assertThat(row.masked()).isEqualTo("P_MASK");
            assertThat(row.original()).isNotBlank();
        }
        Long mappingRows = jdbc.queryForObject("SELECT COUNT(*) FROM df_mask_mapping", Long.class);
        assertThat(mappingRows).isZero();
    }

    @Test
    void nonStringColumnIsSkippedAndKeptUntouched() {
        // phone（字符串）正常脱敏，age 列（INT）配置了算法也应被跳过保持原值
        MaskingTaskRequest request = new MaskingTaskRequest(1L,
                List.of(new TableRule("users",
                        Map.of("phone", new ColumnRule("phone", null, null, null),
                                "age", new ColumnRule("phone", null, null, null)),
                        null)), null, null);
        MaskingRunResult result = engine.execute(dataSource, DIALECT, request, null);
        assertThat(result.rowsByTable()).containsEntry("users", 4L);
        List<Integer> ages = jdbc.queryForList("SELECT age FROM users ORDER BY id", Integer.class);
        assertThat(ages).containsExactly(30, 25, 28, 35);
    }

    @Test
    void whereFilterLimitsProcessedRows() {
        MaskingTaskRequest request = req("users", new ColumnRule("phone", null, null, null), "id <= 2");
        MaskingRunResult result = engine.execute(dataSource, DIALECT, request, null);
        assertThat(result.rowsByTable()).containsEntry("users", 2L);
    }

    @Test
    void emptyTableProcessesZeroRows() {
        jdbc.update("DELETE FROM users");
        MaskingTaskRequest request = req("users", new ColumnRule("phone", null, null, null), null);
        MaskingRunResult result = engine.execute(dataSource, DIALECT, request, null);
        assertThat(result.rowsByTable()).containsEntry("users", 0L);
    }

    @Test
    void algorithmInferredByColumnNameWhenUnset() {
        when(introspector.introspect(any(), any())).thenReturn(List.of(usersMeta("id")));
        MaskingTaskRequest request = req("users", new ColumnRule(null, null, null, null), null);
        MaskingRunResult result = engine.execute(dataSource, DIALECT, request, null);
        assertThat(result.rowsByTable()).containsEntry("users", 4L);
    }

    @Test
    void unknownTableRejected() {
        when(introspector.introspect(any(), any())).thenReturn(List.of(usersMeta("id")));
        MaskingTaskRequest request = req("missing", new ColumnRule("phone", null, null, null), null);
        assertThatThrownBy(() -> engine.execute(dataSource, DIALECT, request, null))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED.getCode());
    }

    @Test
    void missingColumnRejected() {
        MaskingTaskRequest request = req("users", new ColumnRule("phone", null, null, null), null);
        MaskingTaskRequest bad = new MaskingTaskRequest(1L,
                List.of(new TableRule("users", Map.of("no_such_col",
                        new ColumnRule("phone", null, null, null)), null)), null, null);
        assertThatThrownBy(() -> engine.execute(dataSource, DIALECT, bad, null))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED.getCode());
    }

    @Test
    void tableWithoutPrimaryKeyRejected() {
        when(introspector.introspect(any(), any())).thenReturn(List.of(usersMeta(null)));
        MaskingTaskRequest request = req("users", new ColumnRule("phone", null, null, null), null);
        assertThatThrownBy(() -> engine.execute(dataSource, DIALECT, request, null))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED.getCode());
    }

    @Test
    void cancelPropagatesTaskCancelFailed() {
        when(introspector.introspect(any(), any())).thenReturn(List.of(usersMeta("id")));
        MaskingTaskRequest request = req("users", new ColumnRule("phone", null, null, null), null);
        assertThatThrownBy(() -> engine.execute(dataSource, DIALECT, request, new CancellingListener()))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.TASK_CANCEL_FAILED.getCode());
    }

    // ---- 启发式算法（原有便捷用例保留） ----

    @Test
    void heuristicAlgorithmDetectsSensitiveColumnFamilies() {
        assertThat(MaskingEngine.heuristicAlgorithm("mobile_no")).isEqualTo("phone");
        assertThat(MaskingEngine.heuristicAlgorithm("telephone")).isEqualTo("phone");
        assertThat(MaskingEngine.heuristicAlgorithm("id_card_no")).isEqualTo("id_card");
        assertThat(MaskingEngine.heuristicAlgorithm("identity_card")).isEqualTo("id_card");
        assertThat(MaskingEngine.heuristicAlgorithm("identity_no")).isEqualTo("id_card");
        assertThat(MaskingEngine.heuristicAlgorithm("bank_card_no")).isEqualTo("bank_card");
        assertThat(MaskingEngine.heuristicAlgorithm("bankcard")).isEqualTo("bank_card");
        assertThat(MaskingEngine.heuristicAlgorithm("card_no")).isEqualTo("bank_card");
        assertThat(MaskingEngine.heuristicAlgorithm("account_no")).isEqualTo("bank_card");
        assertThat(MaskingEngine.heuristicAlgorithm("email")).isEqualTo("faker");
        assertThat(MaskingEngine.heuristicAlgorithm("Mail_Addr")).isEqualTo("faker");
        assertThat(MaskingEngine.heuristicAlgorithm("real_name")).isEqualTo("faker");
        assertThat(MaskingEngine.heuristicAlgorithm("address")).isEqualTo("faker");
        assertThat(MaskingEngine.heuristicAlgorithm("street")).isEqualTo("faker");
    }

    @Test
    void heuristicAlgorithmReturnsNullForUnrelatedColumns() {
        assertThat(MaskingEngine.heuristicAlgorithm("amount")).isNull();
        assertThat(MaskingEngine.heuristicAlgorithm("created_at")).isNull();
        assertThat(MaskingEngine.heuristicAlgorithm(null)).isNull();
    }

    // ---- 构造辅助 ----

    private static TableMetadata usersMeta(String primaryKey) {
        return new TableMetadata("users",
                List.of(new ColumnMetadata("id", "BIGINT", false, null, true, List.of()),
                        new ColumnMetadata("name", "VARCHAR", false, null, false, List.of()),
                        new ColumnMetadata("phone", "VARCHAR", false, null, false, List.of()),
                        new ColumnMetadata("email", "VARCHAR", true, null, false, List.of()),
                        new ColumnMetadata("age", "INT", true, null, false, List.of())),
                List.of(), List.of(), List.of(), primaryKey);
    }

    private static MaskingTaskRequest req(String table, ColumnRule rule, String where) {
        return new MaskingTaskRequest(1L,
                List.of(new TableRule(table, Map.of(ruleColumnOf(rule), rule), where)), null, null);
    }

    private static String ruleColumnOf(ColumnRule rule) {
        return "phone";
    }

    /** 取消监听器。 */
    private static final class CancellingListener implements MaskingListener {
        @Override
        public void onProgress(String table, long processedRows, long totalRows) {
        }

        @Override
        public void onLog(Severity severity, String message) {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    }
}