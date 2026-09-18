package com.relivus.repository;

import com.relivus.entity.ConnectionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.lang.reflect.Field;
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
 * 连接仓库测试。
 */
@SuppressWarnings("unchecked")
class ConnectionRepositoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ConnectionRepository repository = new ConnectionRepository(jdbc);

    @Test
    void findAllAndFindByIdAndFindByName() {
        ConnectionEntity entity = connectionEntity(1L);
        when(jdbc.query(eq("SELECT * FROM df_connection ORDER BY id"), any(RowMapper.class)))
                .thenReturn(List.of(entity));
        when(jdbc.query(eq("SELECT * FROM df_connection WHERE id = ?"), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(entity));
        when(jdbc.query(eq("SELECT * FROM df_connection WHERE id = ?"), any(RowMapper.class), eq(2L)))
                .thenReturn(List.of());
        when(jdbc.query(eq("SELECT * FROM df_connection WHERE name = ?"), any(RowMapper.class), eq("prod")))
                .thenReturn(List.of(entity));

        assertThat(repository.findAll()).containsExactly(entity);
        assertThat(repository.findById(1L)).isEqualTo(Optional.of(entity));
        assertThat(repository.findById(2L)).isEmpty();
        assertThat(repository.findByName("prod")).contains(entity);
    }

    @Test
    void existsByNameChecksCount() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("prod"))).thenReturn(2);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("new"))).thenReturn(0);
        assertThat(repository.existsByName("prod")).isTrue();
        assertThat(repository.existsByName("new")).isFalse();
    }

    @Test
    void insertReturnsGeneratedKey() throws Exception {
        doAnswer(inv -> {
            KeyHolder holder = inv.getArgument(1);
            if (holder instanceof GeneratedKeyHolder) {
                Field field = GeneratedKeyHolder.class.getDeclaredField("keyList");
                field.setAccessible(true);
                field.set(holder, new LinkedList<>(List.of(Map.of("GENERATED_KEY", 5L))));
            }
            return 1;
        }).when(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        assertThat(repository.insert(connectionEntity(null))).isEqualTo(5L);
        verify(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    void updateAndDeleteDelegate() {
        // update：9 个占位符（name..updated_at..id）；delete：1 个参数（id）
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), eq(1L)))
                .thenReturn(1);
        when(jdbc.update(anyString(), eq(1L))).thenReturn(1);
        assertThat(repository.update(connectionEntity(1L))).isEqualTo(1);
        assertThat(repository.delete(1L)).isEqualTo(1);
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), eq(1L));
        verify(jdbc).update(anyString(), eq(1L));
    }

    private static ConnectionEntity connectionEntity(Long id) {
        ConnectionEntity entity = new ConnectionEntity();
        if (id != null) {
            entity.setId(id);
        }
        entity.setName("prod");
        entity.setDbType("mysql");
        entity.setHost("127.0.0.1");
        entity.setPort(3306);
        entity.setDatabaseName("db");
        entity.setUsername("root");
        entity.setPasswordCipher("cipher");
        return entity;
    }
}