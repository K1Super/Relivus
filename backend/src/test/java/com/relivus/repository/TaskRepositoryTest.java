package com.relivus.repository;

import com.relivus.entity.TaskEntity;
import com.relivus.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务仓库测试（mock JdbcTemplate，验证 SQL 与 ROW_MAPPER 路径）。
 */
@SuppressWarnings("unchecked")
class TaskRepositoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TaskRepository repository = new TaskRepository(jdbc);

    @Test
    void findAllClampsLimitAndOffset() {
        when(jdbc.query(eq("SELECT * FROM df_task ORDER BY id DESC LIMIT ? OFFSET ?"),
                any(RowMapper.class), eq(200), eq(0))).thenReturn(List.of());
        assertThat(repository.findAll(99999, -5)).isEmpty();
    }

    @Test
    void findByIdMapsPresentOrEmpty() {
        TaskEntity task = taskEntity();
        when(jdbc.query(eq("SELECT * FROM df_task WHERE id = ?"), any(RowMapper.class), eq(7L)))
                .thenReturn(List.of(task));
        when(jdbc.query(eq("SELECT * FROM df_task WHERE id = ?"), any(RowMapper.class), eq(8L)))
                .thenReturn(List.of());

        Optional<TaskEntity> found = repository.findById(7L);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(7L);
        assertThat(repository.findById(8L)).isEmpty();
    }

    @Test
    void insertReturnsGeneratedKey() throws Exception {
        stubGeneratedKey(42L);
        TaskEntity task = taskEntity();
        Long id = repository.insert(task);
        assertThat(id).isEqualTo(42L);
        verify(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    void updateAndUpkeepMethodsDelegate() {
        // JdbcTemplate varargs：按各方法占位符个数打桩，末位参数用 eq(7L) 使 varargs 数组正常收包
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), eq(7L)))
                .thenReturn(1); // update(entity)：9 个占位符
        when(jdbc.update(anyString(), any(), any(), eq(7L))).thenReturn(1);  // updateProgress：3 个占位符
        when(jdbc.update(anyString(), any(), any(), any(), any(), eq(7L))).thenReturn(1); // updateStatus：5 个占位符
        when(jdbc.update(anyString(), any(), eq(7L))).thenReturn(1);         // markRunning / updateDataBaseline：2 个占位符
        when(jdbc.update(anyString(), eq(7L))).thenReturn(1);                // markCancelRequested：1 个占位符

        TaskEntity task = taskEntity();
        assertThat(repository.update(task)).isEqualTo(1);
        assertThat(repository.updateProgress(7L, 50, 100)).isEqualTo(1);
        assertThat(repository.updateStatus(7L, TaskStatus.SUCCESS, 100, null, LocalDateTime.now())).isEqualTo(1);
        assertThat(repository.markRunning(7L, LocalDateTime.now())).isEqualTo(1);
        assertThat(repository.markCancelRequested(7L)).isEqualTo(1);
        assertThat(repository.updateDataBaseline(7L, "{\"users\":3}")).isEqualTo(1);
    }

    @Test
    void isCancelRequestedReadsBooleanFlag() {
        when(jdbc.queryForObject(eq("SELECT cancel_requested FROM df_task WHERE id = ?"),
                eq(Boolean.class), eq(7L))).thenReturn(Boolean.TRUE);
        when(jdbc.queryForObject(eq("SELECT cancel_requested FROM df_task WHERE id = ?"),
                eq(Boolean.class), eq(8L))).thenReturn(null);
        assertThat(repository.isCancelRequested(7L)).isTrue();
        assertThat(repository.isCancelRequested(8L)).isFalse();
    }

    private static TaskEntity taskEntity() {
        TaskEntity task = new TaskEntity();
        task.setId(7L);
        task.setTaskType("generation");
        task.setConnectionId(3L);
        task.setConfigJson("{}");
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setTotalRows(0);
        task.setProcessedRows(0);
        task.setCancelRequested(false);
        return task;
    }

    /** 向 GeneratedKeyHolder 反射注入生成键，模拟真实自增回填。 */
    private void stubGeneratedKey(long key) throws Exception {
        doAnswer(inv -> {
            KeyHolder holder = inv.getArgument(1);
            if (holder instanceof GeneratedKeyHolder) {
                Field field = GeneratedKeyHolder.class.getDeclaredField("keyList");
                field.setAccessible(true);
                field.set(holder, new LinkedList<>(List.of(Map.of("GENERATED_KEY", key))));
            }
            return 1;
        }).when(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }
}