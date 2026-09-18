package com.relivus.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Flyway 迁移配置：仅迁移元数据库。
 *
 * <p>Spring Boot 自动 Flyway 在 application.yml 中通过 {@code spring.flyway.enabled=false} 关闭，
 * 由本 Bean 接管：按元数据库类型自动选择 {@code classpath:db/migration/{mysql,postgresql}}。
 * 目标库绝不执行 Flyway。生产环境禁止 clean（{@code cleanDisabled=true}）。
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean(initMethod = "migrate", destroyMethod = "")
    public Flyway flyway(@Qualifier(DataSourceConfig.META_DATASOURCE) DataSource metaDataSource) {
        String dialect = detectDialect(metaDataSource);
        validateMigrationScripts(dialect);
        Flyway flyway = Flyway.configure()
                .dataSource(metaDataSource)
                .locations("classpath:db/migration/" + dialect)
                .cleanDisabled(true)
                .baselineOnMigrate(true)
                .load();
        log.info("Flyway configured for dialect={}, clean disabled, meta datasource only", dialect);
        return flyway;
    }

    /**
     * 启动快速失败：dialect 目录必须至少含一个 V1 脚本，否则终止启动，拒绝带病运行。
     */
    static void validateMigrationScripts(String dialect) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath:db/migration/" + dialect + "/V*.sql");
            boolean hasV1 = false;
            for (Resource r : resources) {
                if (r.getFilename() != null && r.getFilename().startsWith("V1")) {
                    hasV1 = true;
                }
            }
            if (resources.length == 0 || !hasV1) {
                throw new IllegalStateException(
                        "Flyway migration scripts missing for dialect '" + dialect
                                + "' at classpath:db/migration/" + dialect);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to locate Flyway migration scripts for dialect '" + dialect + "'", e);
        }
    }

    static String detectDialect(DataSource ds) {
        try (Connection c = ds.getConnection()) {
            String name = c.getMetaData().getDatabaseProductName().toLowerCase();
            if (name.contains("mysql") || name.contains("mariadb")) {
                return "mysql";
            }
            if (name.contains("postgresql")) {
                return "postgresql";
            }
            throw new IllegalStateException("Unsupported meta database product: " + name);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to detect meta database dialect", e);
        }
    }
}