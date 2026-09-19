package com.relivus.service;

import com.relivus.config.RelivusProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 目标库动态数据源注册表。
 *
 * <p>按 {@code connectionId} 动态创建并缓存目标库 HikariDataSource，连接池最大 20 个，
 * 空闲超过 10 分钟自动关闭。目标库绝不执行 Flyway。
 */
@Component
public class TargetDataSourceRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(TargetDataSourceRegistry.class);

    private final RelivusProperties props;
    private final Map<Long, Entry> pools = new ConcurrentHashMap<>();
    private final AtomicLong accessCounter = new AtomicLong();

    public TargetDataSourceRegistry(RelivusProperties props) {
        this.props = props;
    }

    /** 按连接 ID 获取数据源；不存在则创建，创建失败抛异常。 */
    public DataSource get(Long connectionId, ConnectionParams params) {
        Entry existing = pools.get(connectionId);
        if (existing != null) {
            existing.touch();
            return existing.dataSource;
        }
        synchronized (this) {
            Entry recheck = pools.get(connectionId);
            if (recheck != null) {
                recheck.touch();
                return recheck.dataSource;
            }
            enforcePoolLimit();
            HikariDataSource ds = createDataSource(connectionId, params);
            Entry entry = new Entry(ds);
            pools.put(connectionId, entry);
            return ds;
        }
    }

    /** 数据源是否存在且未被关闭。 */
    public boolean contains(Long connectionId) {
        return pools.containsKey(connectionId);
    }

    /** 关闭并移除指定连接的数据源（连接更新/删除时调用）。 */
    public void evict(Long connectionId) {
        Entry removed = pools.remove(connectionId);
        if (removed != null) {
            removed.close();
        }
    }

    /** idle 超时回收：每 60 秒扫描一次，空闲超过配置时长（默认 10 分钟）的池关闭。 */
    @Scheduled(fixedDelay = 60_000)
    public void evictIdle() {
        long idleMillis = props.getDatasource().getIdleTimeoutMinutes() * 60_000L;
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, Entry> e : pools.entrySet()) {
            if (now - e.getValue().lastAccessMs > idleMillis) {
                LOG.info("Evicting idle target datasource, connectionId={}", e.getKey());
                evict(e.getKey());
            }
        }
    }

    /** 达到 20 个连接池上限时，关闭最久未使用的空闲池。 */
    private void enforcePoolLimit() {
        int max = props.getDatasource().getMaxPoolSize();
        while (pools.size() >= max) {
            Optional<Map.Entry<Long, Entry>> lru = pools.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().lastAccessMs));
            if (lru.isEmpty()) {
                break;
            }
            LOG.warn("Target datasource pool limit reached ({}), evicting LRU connectionId={}",
                    max, lru.get().getKey());
            evict(lru.get().getKey());
        }
    }

    private HikariDataSource createDataSource(Long connectionId, ConnectionParams params) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("relivus-target-" + connectionId);
        ds.setJdbcUrl(params.jdbcUrl());
        ds.setUsername(params.username());
        ds.setPassword(params.password());
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(0);
        ds.setIdleTimeout(600_000L);
        ds.setConnectionTimeout(10_000L);
        ds.setDriverClassName(params.driverClassName());
        return ds;
    }

    /** 连接参数（由 ConnectionService 从实体 + 方言装配，密码不为空时使用）。 */
    public record ConnectionParams(String jdbcUrl, String username, String password, String driverClassName) {
    }

    private static final class Entry {
        final HikariDataSource dataSource;
        volatile long lastAccessMs;

        Entry(HikariDataSource dataSource) {
            this.dataSource = dataSource;
            this.lastAccessMs = System.currentTimeMillis();
        }

        void touch() {
            this.lastAccessMs = System.currentTimeMillis();
        }

        void close() {
            try {
                dataSource.close();
            } catch (RuntimeException e) {
                LOG.warn("Failed to close target datasource: {}", e.getMessage());
            }
        }
    }
}