package com.relivus.controller;

import com.relivus.common.api.ApiResponse;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.dialect.DatabaseDialect;
import com.relivus.dto.DependencyGraphResponse;
import com.relivus.dto.SchemaTableSummary;
import com.relivus.dto.TableDetailResponse;
import com.relivus.generator.TableDependencyGraph;
import com.relivus.generator.TopologicalSorter;
import com.relivus.schema.SchemaCache;
import com.relivus.schema.SchemaIntrospector;
import com.relivus.schema.model.TableMetadata;
import com.relivus.service.IConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Schema 内省接口。元数据经 {@link SchemaCache} 缓存（10 分钟）。
 */
@Tag(name = "Schema 内省")
@RestController
@RequestMapping("/api/schema")
public class SchemaController {

    private final IConnectionService connectionService;
    private final SchemaIntrospector introspector;
    private final SchemaCache schemaCache;

    public SchemaController(IConnectionService connectionService, SchemaIntrospector introspector,
                            SchemaCache schemaCache) {
        this.connectionService = connectionService;
        this.introspector = introspector;
        this.schemaCache = schemaCache;
    }

    @Operation(summary = "表列表")
    @GetMapping("/{connId}/tables")
    public ApiResponse<List<SchemaTableSummary>> tables(@PathVariable Long connId) {
        List<TableMetadata> tables = introspect(connId);
        List<SchemaTableSummary> summaries = tables.stream()
                .map(t -> new SchemaTableSummary(t.tableName(), t.columns().size(), t.primaryKey()))
                .sorted(Comparator.comparing(SchemaTableSummary::tableName))
                .toList();
        return ApiResponse.ok(summaries);
    }

    @Operation(summary = "表详情")
    @GetMapping("/{connId}/tables/{table}")
    public ApiResponse<TableDetailResponse> table(@PathVariable Long connId, @PathVariable String table) {
        TableMetadata meta = introspect(connId).stream()
                .filter(t -> t.tableName().equals(table))
                .findFirst()
                .orElseThrow(() -> new RelivusException(ErrorCode.VALIDATION_FAILED, "表不存在：" + table));
        return ApiResponse.ok(TableDetailResponse.from(meta));
    }

    @Operation(summary = "依赖图（拓扑序与循环）")
    @GetMapping("/{connId}/dependencies")
    public ApiResponse<DependencyGraphResponse> dependencies(@PathVariable Long connId) {
        List<TableMetadata> tables = introspect(connId);
        TableDependencyGraph graph = new TableDependencyGraph();
        graph.build(tables);
        TopologicalSorter.TopologicalSortResult sort = TopologicalSorter.sort(graph);
        return ApiResponse.ok(new DependencyGraphResponse(
                new ArrayList<>(sort.order()), sort.cycles()));
    }

    private List<TableMetadata> introspect(Long connId) {
        return schemaCache.get(connId, () -> {
            DataSourceResolver resolver = new DataSourceResolver(connectionService);
            java.sql.Connection connection = resolver.open(connId);
            try {
                return introspector.introspect(connection, resolver.dialect(connId));
            } finally {
                try {
                    connection.close();
                } catch (java.sql.SQLException ignored) {
                    // 连接归还失败不影响内省结果，job 结束后仍会被池回收
                }
            }
        });
    }

    /** 由 IConnectionService 拆装数据源与方言，避免在 Controller 内直连 JDBC 细节。 */
    private static final class DataSourceResolver {
        private final IConnectionService connectionService;

        DataSourceResolver(IConnectionService connectionService) {
            this.connectionService = connectionService;
        }

        Connection open(Long connId) {
            try {
                return connectionService.resolveDataSource(connId).getConnection();
            } catch (java.sql.SQLException e) {
                throw new RelivusException(ErrorCode.CONNECTION_FAILED,
                        "目标库连接失败：" + e.getMessage(), e);
            }
        }

        DatabaseDialect dialect(Long connId) {
            return connectionService.dialect(connId);
        }
    }
}