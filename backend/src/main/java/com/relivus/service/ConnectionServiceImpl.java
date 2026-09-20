package com.relivus.service;

import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dialect.DatabaseDialect;
import com.relivus.dialect.DialectRegistry;
import com.relivus.dto.ConnectionTestResult;
import com.relivus.dto.CreateConnectionRequest;
import com.relivus.dto.ConnectionResponse;
import com.relivus.entity.ConnectionEntity;
import com.relivus.repository.AuditLogRepository;
import com.relivus.repository.ConnectionRepository;
import com.relivus.schema.SchemaCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.MDC;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 目标库连接管理服务。
 *
 * <p>职责：连接 CRUD、测试、密码 AES-GCM 加解密、动态数据源装配。连接密码解密后仅在
 * 内存中短暂存在，用于创建数据源，绝不出现在日志与响应中。
 */
@Service
public class ConnectionServiceImpl implements IConnectionService {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionServiceImpl.class);

    private final ConnectionRepository repository;
    private final CryptoService cryptoService;
    private final TargetDataSourceRegistry dataSourceRegistry;
    private final DialectRegistry dialectRegistry;
    private final AuditLogRepository auditLogRepository;
    private final SchemaCache schemaCache;

    public ConnectionServiceImpl(ConnectionRepository repository, CryptoService cryptoService,
                             TargetDataSourceRegistry dataSourceRegistry, DialectRegistry dialectRegistry,
                             AuditLogRepository auditLogRepository, SchemaCache schemaCache) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.dataSourceRegistry = dataSourceRegistry;
        this.dialectRegistry = dialectRegistry;
        this.auditLogRepository = auditLogRepository;
        this.schemaCache = schemaCache;
    }

    @Override
    public List<ConnectionResponse> list() {
        return repository.findAll().stream().map(ConnectionResponse::from).toList();
    }

    @Override
    @Transactional
    public ConnectionResponse create(CreateConnectionRequest request) {
        validateDbType(request.dbType());
        if (repository.existsByName(request.name())) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                    "Connection name already exists: " + request.name());
        }
        if (!request.hasPassword()) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED, "Password is required when creating connection");
        }
        ConnectionEntity entity = new ConnectionEntity();
        entity.setName(request.name());
        entity.setDbType(normalizeDbType(request.dbType()));
        entity.setHost(request.host());
        entity.setPort(request.port());
        entity.setDatabaseName(request.database());
        entity.setUsername(request.username());
        entity.setPasswordCipher(cryptoService.encrypt(request.password()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        Long id = repository.insert(entity);
        auditLogRepository.insert("connection_create", entity.getName(), null, "success", MDC.get("traceId"));
        return ConnectionResponse.from(repository.findById(id).orElseThrow());
    }

    @Override
    @Transactional
    public ConnectionResponse update(Long id, CreateConnectionRequest request) {
        ConnectionEntity entity = requireEntity(id);
        validateDbType(request.dbType());
        if (!entity.getName().equals(request.name()) && repository.existsByName(request.name())) {
            throw new RelivusException(ErrorCode.VALIDATION_FAILED,
                    "Connection name already exists: " + request.name());
        }
        entity.setName(request.name());
        entity.setDbType(normalizeDbType(request.dbType()));
        entity.setHost(request.host());
        entity.setPort(request.port());
        entity.setDatabaseName(request.database());
        entity.setUsername(request.username());
        if (request.isUpdatePassword() && request.hasPassword()) {
            entity.setPasswordCipher(cryptoService.encrypt(request.password()));
        }
        entity.setUpdatedAt(LocalDateTime.now());
        repository.update(entity);
        markConnectionChanged(id);
        auditLogRepository.insert("connection_update", entity.getName(), null, "success", MDC.get("traceId"));
        return ConnectionResponse.from(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ConnectionEntity entity = requireEntity(id);
        markConnectionChanged(id);
        repository.delete(id);
        auditLogRepository.insert("connection_delete", entity.getName(), null, "success", MDC.get("traceId"));
    }

    /** 测试未保存连接参数（Fast fail：连接失败即返回 1001，供 UI 即时反馈）。 */
    @Override
    public ConnectionTestResult test(CreateConnectionRequest request) {
        validateDbType(request.dbType());
        String dbType = normalizeDbType(request.dbType());
        DatabaseDialect dialect = dialectRegistry.resolveByJdbcUrl(driverFromDialect(dbType));
        String jdbcUrl = dialect.buildJdbcUrl(request.host(), request.port(), request.database());
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(request.username());
        ds.setPassword(request.password() == null ? "" : request.password());
        ds.setDriverClassName(driverClassName(dbType));
        ds.setMaximumPoolSize(1);
        ds.setConnectionTimeout(10_000L);
        try (Connection connection = ds.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            return new ConnectionTestResult(true, "Connection ok",
                    meta.getDatabaseProductName(), meta.getDatabaseProductVersion());
        } catch (SQLException e) {
            LOG.warn("Connection test failed, host={}, database={}, err={}",
                    request.host(), request.database(), e.getMessage());
            throw new RelivusException(ErrorCode.CONNECTION_FAILED, "Connection failed: " + e.getMessage(), e);
        } catch (RelivusException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Connection test failed, host={}, err={}", request.host(), e.getMessage());
            throw new RelivusException(ErrorCode.CONNECTION_FAILED, "Connection failed: " + e.getMessage(), e);
        } finally {
            ds.close();
        }
    }

    /**
     * 测试已保存连接。
     */
    @Override
    public ConnectionTestResult testById(Long id) {
        ConnectionEntity entity = requireEntity(id);
        DataSource ds = resolveDataSource(id);
        try (Connection connection = ds.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            return new ConnectionTestResult(true, "Connection ok",
                    meta.getDatabaseProductName(), meta.getDatabaseProductVersion());
        } catch (RelivusException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Connection test failed, connectionId={}, err={}", id, e.getMessage());
            throw new RelivusException(ErrorCode.CONNECTION_FAILED, "Connection failed: " + e.getMessage(), e);
        }
    }

    /** 拿取目标库 DataSource（动态创建并缓存，密码仅内存短存）。 */
    @Override
    public DataSource resolveDataSource(Long connectionId) {
        ConnectionEntity entity = requireEntity(connectionId);
        String password;
        try {
            password = cryptoService.decrypt(entity.getPasswordCipher());
        } catch (RelivusException e) {
            throw new RelivusException(ErrorCode.CONNECTION_FAILED, "Failed to decrypt saved password", e);
        }
        DatabaseDialect dialect = dialectRegistry.resolveByJdbcUrl(driverFromDialect(entity.getDbType()));
        String jdbcUrl = dialect.buildJdbcUrl(entity.getHost(), entity.getPort(), entity.getDatabaseName());
        TargetDataSourceRegistry.ConnectionParams params = new TargetDataSourceRegistry.ConnectionParams(
                jdbcUrl, entity.getUsername(), password,
                driverClassName(entity.getDbType()));
        return dataSourceRegistry.get(connectionId, params);
    }

    /** 获取连接方言。 */
    @Override
    public DatabaseDialect dialect(Long connectionId) {
        ConnectionEntity entity = requireEntity(connectionId);
        return dialectRegistry.resolveByJdbcUrl(driverFromDialect(entity.getDbType()));
    }

    /** 连接实体是否存在。 */
    @Override
    public ConnectionEntity requireEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RelivusException(ErrorCode.VALIDATION_FAILED, "Connection not found: " + id));
    }

    private void markConnectionChanged(Long id) {
        dataSourceRegistry.evict(id);
        schemaCache.evict(id);
    }

    private static void validateDbType(String dbType) {
        if (dbType == null || (!"mysql".equalsIgnoreCase(dbType) && !"postgresql".equalsIgnoreCase(dbType))) {
            throw new RelivusException(ErrorCode.UNSUPPORTED_DATABASE,
                    "Unsupported db type: " + dbType + ", supported: mysql, postgresql");
        }
    }

    private static String normalizeDbType(String dbType) {
        return dbType.toLowerCase(Locale.ROOT);
    }

    private static String driverFromDialect(String dbType) {
        return switch (normalizeDbType(dbType)) {
            case "mysql" -> "jdbc:mysql:";
            case "postgresql" -> "jdbc:postgresql:";
            default -> throw new RelivusException(ErrorCode.UNSUPPORTED_DATABASE,
                    "Unsupported db type: " + dbType);
        };
    }

    private static String driverClassName(String dbType) {
        return switch (normalizeDbType(dbType)) {
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "postgresql" -> "org.postgresql.Driver";
            default -> throw new RelivusException(ErrorCode.UNSUPPORTED_DATABASE,
                    "Unsupported db type: " + dbType);
        };
    }
}