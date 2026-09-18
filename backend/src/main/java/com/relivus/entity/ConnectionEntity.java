package com.relivus.entity;

import java.time.LocalDateTime;

/**
 * 目标库连接实体（df_connection）。
 *
 * <p>密码以 AES-GCM 密文保存于 {@code passwordCipher}，绝不以明文驻留实体或日志。
 */
public class ConnectionEntity {

    private Long id;
    private String name;
    /** mysql / postgresql。 */
    private String dbType;
    private String host;
    private int port;
    private String databaseName;
    private String username;
    /** AES-GCM 密文（Base64(IV + cipherText + tag)）。 */
    private String passwordCipher;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordCipher() { return passwordCipher; }
    public void setPasswordCipher(String passwordCipher) { this.passwordCipher = passwordCipher; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}