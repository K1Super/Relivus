package com.relivus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Relivus 全局配置绑定（application.yml 中 {@code relivus.*}）。
 *
 * <p>所有密钥、Token、连接串均通过环境变量注入，配置类仅作绑定，不持有任何明文业务值。
 */
@ConfigurationProperties(prefix = "relivus")
public class RelivusProperties {

    private Meta meta = new Meta();

    private Security security = new Security();

    private Crypto crypto = new Crypto();

    private Masking masking = new Masking();

    private Task task = new Task();

    private Sse sse = new Sse();

    private TargetDatasource datasource = new TargetDatasource();

    private final Ai ai = new Ai();

    /** 元数据库配置（Relivus 自有库）。 */
    public static class Meta {
        /** JDBC 连接串，环境变量 {@code RELIVUS_META_URL} 覆盖。 */
        private String url = "jdbc:mysql://127.0.0.1:3306/relivus_meta";
        private String username = "relivus";
        private String password = "";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /** API 鉴权配置。 */
    public static class Security {
        /** Bearer Token，环境变量 {@code RELIVUS_TOKEN}。 */
        private String token = "";

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    /** 加密配置。 */
    public static class Crypto {
        /** AES-GCM 256 密钥（Base64 32 字节），环境变量 {@code RELIVUS_AES_KEY}。 */
        private String aesKey = "";

        public String getAesKey() { return aesKey; }
        public void setAesKey(String aesKey) { this.aesKey = aesKey; }
    }

    /** 脱敏配置。 */
    public static class Masking {
        /** HMAC-SHA256 密钥，环境变量 {@code RELIVUS_HMAC_KEY}。 */
        private String hmacKey = "";
        /** 当前密钥版本。 */
        private int keyVersion = 1;

        public String getHmacKey() { return hmacKey; }
        public void setHmacKey(String hmacKey) { this.hmacKey = hmacKey; }
        public int getKeyVersion() { return keyVersion; }
        public void setKeyVersion(int keyVersion) { this.keyVersion = keyVersion; }
    }

    /** 任务线程池配置。 */
    public static class Task {
        private int corePoolSize = 4;
        private int maxPoolSize = 8;
        private int queueCapacity = 100;

        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    }

    /** SSE 配置。 */
    public static class Sse {
        private int maxConnections = 20;
        private long heartbeatSeconds = 15;
        /** 连接总超时 30 分钟。 */
        private Duration timeout = Duration.ofMinutes(30);

        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
        public long getHeartbeatSeconds() { return heartbeatSeconds; }
        public void setHeartbeatSeconds(long heartbeatSeconds) { this.heartbeatSeconds = heartbeatSeconds; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }

    /** 目标库连接池配置。 */
    public static class TargetDatasource {
        private int maxPoolSize = 20;
        private long idleTimeoutMinutes = 10;

        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public long getIdleTimeoutMinutes() { return idleTimeoutMinutes; }
        public void setIdleTimeoutMinutes(long idleTimeoutMinutes) { this.idleTimeoutMinutes = idleTimeoutMinutes; }
    }

    /** AI 生成能力配置（OpenAI 兼容上游）。 */
    public static class Ai {
        /** 连接超时（毫秒）。 */
        private int connectTimeoutMs = 5000;
        /** 读超时（毫秒）。 */
        private long readTimeoutMs = 60000;
        /** 每次向 AI 请求的批量值数量。 */
        private int batchSize = 64;
        /** 上游 5xx / IO / 超时 的最大重试次数。 */
        private int maxRetries = 3;

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public long getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(long readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public Crypto getCrypto() { return crypto; }
    public void setCrypto(Crypto crypto) { this.crypto = crypto; }
    public Masking getMasking() { return masking; }
    public void setMasking(Masking masking) { this.masking = masking; }
    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }
    public Sse getSse() { return sse; }
    public void setSse(Sse sse) { this.sse = sse; }
    public TargetDatasource getDatasource() { return datasource; }
    public void setDatasource(TargetDatasource datasource) { this.datasource = datasource; }
    public Ai getAi() { return ai; }
}