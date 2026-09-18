package com.relivus.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计日志仓库测试。
 *
 * <p>JdbcTemplate 的 varargs 方法要求按「每个元素一个匹配器」打桩，才能与真实调用对齐。
 */
class AuditLogRepositoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditLogRepository repository = new AuditLogRepository(jdbc);

    @Test
    void insertDelegatesToJdbcTemplate() {
        when(jdbc.update(any(), any(), any(), any(), any(), any())).thenReturn(1);
        assertThat(repository.insert("connection_create", "prod", "detail", "success", "trace-1")).isEqualTo(1);
        verify(jdbc).update(any(), any(), any(), any(), any(), any());
    }

    @Test
    void threeArgInsertFillsNulls() {
        when(jdbc.update(any(), any(), any(), any(), any(), any())).thenReturn(1);
        assertThat(repository.insert("task_cancel", "t-1", "success")).isEqualTo(1);
        verify(jdbc).update(any(), any(), any(), any(), any(), any());
    }

    @Test
    void countByActionReturnsCountOrZero() {
        when(jdbc.queryForObject(any(), eq(Integer.class), any(), any()))
                .thenReturn(3, null);
        assertThat(repository.countByAction("connection_create", 24)).isEqualTo(3);
        assertThat(repository.countByAction("whatever", 24)).isZero();
    }
}