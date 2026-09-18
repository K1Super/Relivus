package com.relivus.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 主数据源（元数据库）配置。
 *
 * <p>创建 {@code @Primary metaDataSource}，元数据库是 Relivus 自有库，存放连接、任务、日志、
 * 映射与审计数据。目标库连接一律通过 {@link com.relivus.service.TargetDataSourceRegistry}
 * 动态创建，与本数据源严格隔离。
 */
@Configuration
@EnableConfigurationProperties(RelivusProperties.class)
public class DataSourceConfig {

    /** 元数据库 Bean 名称。 */
    public static final String META_DATASOURCE = "metaDataSource";

    @Bean(name = META_DATASOURCE)
    @Primary
    public DataSource metaDataSource(RelivusProperties props) {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(driverFromUrl(props.getMeta().getUrl()))
                .url(props.getMeta().getUrl())
                .username(props.getMeta().getUsername())
                .password(props.getMeta().getPassword())
                .build();
        ds.setMaximumPoolSize(10);
        ds.setPoolName("relivus-meta");
        return ds;
    }

    /** 按 JDBC URL 推断驱动类名，避免在配置中硬编码数据库厂商。 */
    static String driverFromUrl(String url) {
        String lowered = url.toLowerCase();
        if (lowered.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (lowered.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        throw new IllegalStateException("Unsupported meta database url: " + url);
    }
}