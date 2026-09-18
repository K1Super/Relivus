package com.relivus.integration;

import com.relivus.config.TaskExecutorConfig;
import com.relivus.config.RelivusProperties;
import com.relivus.dto.GenerationConfig;
import com.relivus.dto.GenerationConfig.TableConfig;
import com.relivus.entity.TaskEntity;
import com.relivus.generator.DataGenerationEngine;
import com.relivus.generator.ValueGeneratorFactory;
import com.relivus.repository.TaskLogRepository;
import com.relivus.repository.TaskRepository;
import com.relivus.task.SseTaskProgressNotifier;
import com.relivus.task.TaskService;
import com.relivus.task.TaskStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务运行中取消集成测试（DOC-08 / DOC-11.7）。
 *
 * <p>元库用 H2 内存库（df_task/df_task_log），目标库用真实 MySQL 容器；
 * 生成大表使任务运行足够久，中途 cancel 后轮询断言状态变为 CANCELLED。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskCancelIT extends AbstractDatabaseIT {

    private static final class Meta {
        final JdbcDataSource ds = new JdbcDataSource();
        final JdbcTemplate jdbc;

        Meta() throws Exception {
            ds.setURL("jdbc:h2:mem:itmeta;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
            jdbc = new JdbcTemplate(ds);
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS df_task ("
                        + " id BIGINT AUTO_INCREMENT PRIMARY KEY, task_type VARCHAR(32) NOT NULL,"
                        + " connection_id BIGINT NULL, config_json TEXT NULL,"
                        + " status VARCHAR(16) NOT NULL DEFAULT 'PENDING', progress INT NOT NULL DEFAULT 0,"
                        + " total_rows BIGINT NOT NULL DEFAULT 0, processed_rows BIGINT NOT NULL DEFAULT 0,"
                        + " error_message TEXT NULL, cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,"
                        + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, started_at TIMESTAMP NULL,"
                        + " finished_at TIMESTAMP NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS df_task_log ("
                        + " id BIGINT AUTO_INCREMENT PRIMARY KEY, task_id BIGINT NOT NULL,"
                        + " level VARCHAR(16) NOT NULL, message TEXT NOT NULL,"
                        + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            }
        }
    }

    @Test
    void cancellingRunningTaskTransitionsToCancelled() throws Exception {
        // 目标库：准备 customers 大表
        execute(mysqlDataSource(),
                "DROP TABLE IF EXISTS customers",
                "CREATE TABLE customers (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                        + " name VARCHAR(64) NOT NULL, email VARCHAR(128) NULL)");

        Meta meta = new Meta();
        RelivusProperties props = new RelivusProperties();
        props.getTask().setCorePoolSize(1);
        props.getTask().setMaxPoolSize(1);
        props.getTask().setQueueCapacity(10);
        TaskExecutorConfig executorConfig = new TaskExecutorConfig();
        ThreadPoolTaskExecutor executor = executorConfig.taskExecutor(props);

        TaskRepository taskRepository = new TaskRepository(meta.jdbc);
        TaskLogRepository taskLogRepository = new TaskLogRepository(meta.jdbc);
        SseTaskProgressNotifier notifier = new SseTaskProgressNotifier(new com.fasterxml.jackson.databind.ObjectMapper());
        TaskService service = new TaskService(taskRepository, taskLogRepository, executor, notifier);

        Long taskId = service.createTask("generation", 1L, "{}");
        DataGenerationEngine engine = new DataGenerationEngine(new ValueGeneratorFactory(), INTROSPECTOR);
        CountDownLatch started = new CountDownLatch(1);

        service.executeAsync(taskId, ctx -> {
            ctx.log("INFO", "开始生成");
            started.countDown();
            engine.execute(mysqlDataSource(), MYSQL_DIALECT,
                    new GenerationConfig(1L, List.of(new TableConfig("customers", 200_000, Map.of())),
                            "UNIFORM", true, 1000),
                    ctx.generationListener());
        });

        assertThat(started.await(30, TimeUnit.SECONDS)).isTrue();
        // 等待任务进入 RUNNING
        TaskEntity running = waitForStatus(service, taskId, TaskStatus.RUNNING, 30);
        assertThat(running.getStatus()).isEqualTo(TaskStatus.RUNNING);

        service.cancel(taskId);
        TaskEntity cancelled = waitForStatus(service, taskId, TaskStatus.CANCELLED, 60);
        assertThat(cancelled.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        Long rows = new JdbcTemplate(mysqlDataSource()).queryForObject(
                "SELECT COUNT(*) FROM customers", Long.class);
        // 取消后可能部分行已写入，但不允许出现完整 20 万行
        assertThat(rows).isLessThan(200_000L);
        executor.shutdown();
    }

    private TaskEntity waitForStatus(TaskService service, Long taskId, TaskStatus status, long seconds)
            throws InterruptedException {
        TaskEntity entity = null;
        long deadline = System.currentTimeMillis() + seconds * 1000;
        while (System.currentTimeMillis() < deadline) {
            entity = service.getTask(taskId);
            if (entity.getStatus() == status) {
                return entity;
            }
            Thread.sleep(50);
        }
        return entity;
    }
}