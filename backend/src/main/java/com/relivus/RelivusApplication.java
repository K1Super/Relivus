package com.relivus;

import com.relivus.config.TaskExecutorConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Relivus 应用唯一入口。
 *
 * <p>仅承担应用装配职责：包扫描、异步/调度能力开启。所有业务逻辑均下沉至分层模块，
 * 入口不包含任何业务代码与硬编码配置。启动所需的元数据库、密钥、Token 等核心配置
 * 由 {@link com.relivus.config.StartupConfigValidator} 校验，缺失即快速失败。
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@Import(TaskExecutorConfig.class)
public class RelivusApplication {

    public static void main(String[] args) {
        SpringApplication.run(RelivusApplication.class, args);
    }
}