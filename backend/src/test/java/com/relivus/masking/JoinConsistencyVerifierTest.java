package com.relivus.masking;

import com.relivus.dialect.MySQLDialect;
import com.relivus.dialect.PostgreSQLDialect;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JOIN 一致性验证器测试（mock JdbcTemplate：快照创建/校验/清理）。
 */
class JoinConsistencyVerifierTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JoinConsistencyVerifier verifier = new JoinConsistencyVerifier();

    @Test
    void createSnapshotBuildsMySqlDdlAndInsert() {
        verifier.createSnapshot(jdbc, new MySQLDialect(), "df_snap_order",
                "order", "id", "user_id", "id > 100");

        verify(jdbc, atLeastOnce()).execute(anyString()); // DROP IF EXISTS + CREATE TABLE（幂等清理）
        verify(jdbc).update(anyString());
    }

    @Test
    void verifyPassesWhenJoinMatchesSnapshots() {
        when(jdbc.queryForList(eq("SELECT pk_value, join_key FROM " + new MySQLDialect().quoteIdentifier("df_snap_order_1"))))
                .thenReturn(List.of(Map.of("pk_value", "1", "join_key", "42")));
        when(jdbc.queryForList("SELECT user_id FROM order WHERE id = ?", "1"))
                .thenReturn(List.of(Map.of("user_id", "42")));

        JoinConsistencyVerifier.JoinVerificationResult result = verifier.verify(
                jdbc, new MySQLDialect(), "df_snap_order_1",
                "SELECT user_id FROM order WHERE id = ?", "id", "user_id");

        assertThat(result.consistent()).isTrue();
        assertThat(result.checkedRows()).isEqualTo(1);
        assertThat(result.mismatchRows()).isZero();
        verify(jdbc).execute("DROP TABLE IF EXISTS " + new MySQLDialect().quoteIdentifier("df_snap_order_1"));
    }

    @Test
    void verifyFailsOnMismatchedValueAndTreatsEmptyJoinAsConsistent() {
        when(jdbc.queryForList(eq("SELECT pk_value, join_key FROM `df_snap_order_2`")))
                .thenReturn(List.of(
                        Map.of("pk_value", "1", "join_key", "42"),
                        Map.of("pk_value", "2", "join_key", "42")));
        // 行1：JOIN 无结果（脱敏不改变行集合）→ 视为一致；行2：值不相同 → mismatch
        when(jdbc.queryForList("SELECT user_id FROM order WHERE id = ?", "1"))
                .thenReturn(List.of());
        when(jdbc.queryForList("SELECT user_id FROM order WHERE id = ?", "2"))
                .thenReturn(List.of(Map.of("user_id", "7")));

        JoinConsistencyVerifier.JoinVerificationResult result = verifier.verify(
                jdbc, new MySQLDialect(), "df_snap_order_2",
                "SELECT user_id FROM order WHERE id = ?", "id", "user_id");

        assertThat(result.consistent()).isFalse();
        assertThat(result.checkedRows()).isEqualTo(2);
        assertThat(result.mismatchRows()).isEqualTo(1);
    }

    @Test
    void verifyCountsJoinSqlFailureAsMismatch() {
        when(jdbc.queryForList(eq("SELECT pk_value, join_key FROM `df_snap_order_3`")))
                .thenReturn(List.of(Map.of("pk_value", "1", "join_key", "42")));
        when(jdbc.queryForList(anyString(), eq("1"))).thenThrow(new RuntimeException("sql down"));

        JoinConsistencyVerifier.JoinVerificationResult result = verifier.verify(
                jdbc, new MySQLDialect(), "df_snap_order_3",
                "SELECT user_id FROM order WHERE id = ?", "id", "user_id");

        assertThat(result.consistent()).isFalse();
        assertThat(result.mismatchRows()).isEqualTo(1);
    }

    @Test
    void dropSnapshotSwallowsFailure() {
        doThrow(new RuntimeException("drop failed")).when(jdbc).execute(anyString());
        verifier.dropSnapshot(jdbc, new PostgreSQLDialect(), "df_snap_order");
        // 不抛异常即通过（失败仅 WARN 日志）
    }
}