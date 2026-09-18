package com.relivus.repository;

import com.relivus.entity.TaskLogEntity;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务日志仓库测试。
 */
@SuppressWarnings("unchecked")
class TaskLogRepositoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TaskLogRepository repository = new TaskLogRepository(jdbc);

    @Test
    void findByTaskIdRunsLimitQuery() {
        when(jdbc.query(eq("SELECT * FROM df_task_log WHERE task_id = ? ORDER BY id DESC LIMIT 500"),
                any(RowMapper.class), eq(7L))).thenReturn(List.of());
        assertThat(repository.findByTaskId(7L)).isEmpty();
    }

    @Test
    void insertReturnsGeneratedKey() throws Exception {
        doAnswer(inv -> {
            KeyHolder holder = inv.getArgument(1);
            if (holder instanceof GeneratedKeyHolder) {
                Field field = GeneratedKeyHolder.class.getDeclaredField("keyList");
                field.setAccessible(true);
                field.set(holder, new LinkedList<>(List.of(Map.of("GENERATED_KEY", 9L))));
            }
            return 1;
        }).when(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        TaskLogEntity log = new TaskLogEntity();
        log.setTaskId(7L);
        log.setLevel("INFO");
        log.setMessage("x");
        assertThat(repository.insert(log)).isEqualTo(9L);
        verify(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    void nowTimestampIsPresent() {
        assertThat(TaskLogRepository.nowTimestamp()).isNotNull();
    }

    @Test
    void insertTruncatesOversizedMessage() throws Exception {
        String longMsg = "x".repeat(5000);
        doAnswer(inv -> {
            PreparedStatementCreator creator = inv.getArgument(0);
            Connection conn = mock(Connection.class);
            PreparedStatement ps = mock(PreparedStatement.class);
            when(conn.prepareStatement(anyString(), (String[]) any())).thenReturn(ps);
            when(ps.getGeneratedKeys()).thenReturn(mock(ResultSet.class));
            creator.createPreparedStatement(conn);
            verify(ps).setLong(1, 7L);
            verify(ps).setString(2, "INFO");
            verify(ps).setString(3, "x".repeat(4000));
            return 1;
        }).when(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        TaskLogEntity log = new TaskLogEntity();
        log.setTaskId(7L);
        log.setLevel("INFO");
        log.setMessage(longMsg);
        repository.insert(log);
    }

    @Test
    void insertWithoutGeneratedKeyReturnsNull() {
        doAnswer(inv -> 1).when(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        TaskLogEntity log = new TaskLogEntity();
        log.setTaskId(7L);
        log.setLevel("INFO");
        log.setMessage(null); // null 消息按空串截断
        assertThat(repository.insert(log)).isNull();
    }
}