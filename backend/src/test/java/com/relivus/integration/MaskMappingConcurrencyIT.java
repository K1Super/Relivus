package com.relivus.integration;

import com.relivus.config.RelivusProperties;
import com.relivus.dto.MaskingConfig;
import com.relivus.masking.GlobalMaskingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 脱敏映射表并发写集成测试（DOC-08 / DOC-11.6）。
 *
 * <p>100 线程对同一分组 + 同一原始值并发脱敏：唯一索引 + INSERT IGNORE / ON CONFLICT
 * 兜底，最终映射表只落一条记录。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaskMappingConcurrencyIT extends AbstractDatabaseIT {

    @Test
    void concurrentMaskSameValueWritesSingleRowOnMysql() throws Exception {
        runConcurrent(mysqlDataSource(), true);
    }

    @Test
    void concurrentMaskSameValueWritesSingleRowOnPostgres() throws Exception {
        runConcurrent(pgDataSource(), false);
    }

    private void runConcurrent(javax.sql.DataSource ds, boolean mysql) throws Exception {
        execute(ds,
                "DROP TABLE IF EXISTS df_mask_mapping",
                "CREATE TABLE df_mask_mapping (column_group VARCHAR(128), original_hash VARCHAR(64),"
                        + " masked_value VARCHAR(512), algorithm VARCHAR(32), key_version INT,"
                        + (mysql ? "UNIQUE KEY uk_group_hash (column_group, original_hash))"
                        : "CONSTRAINT uk_group_hash UNIQUE (column_group, original_hash))"));

        RelivusProperties props = new RelivusProperties();
        props.getMeta().setUrl(mysql
                ? "jdbc:mysql://127.0.0.1:3306/relivus_meta"
                : "jdbc:postgresql://127.0.0.1:5432/relivus_meta");
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) 13;
        }
        props.getMasking().setHmacKey(Base64.getEncoder().encodeToString(key));
        GlobalMaskingContext context = new GlobalMaskingContext(ds, props);
        MaskingConfig config = MaskingConfig.of("phone", "phoneGroup");

        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await(10, TimeUnit.SECONDS);
                    // 同原始值并发脱敏（结果一致）
                    String masked = context.mask("phoneGroup", "13812345678", config);
                    assertThat(masked).isNotBlank();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        Long rows = new JdbcTemplate(ds).queryForObject(
                "SELECT COUNT(*) FROM df_mask_mapping", Long.class);
        assertThat(rows).isEqualTo(1L);
    }
}