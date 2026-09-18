package com.relivus.generator;

import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.ForeignKeyMetadata;
import com.relivus.schema.model.TableMetadata;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 表依赖图测试（DOC-03：顶点/边、自引用排除、邻接表）。
 */
class TableDependencyGraphTest {

    private static TableMetadata meta(String name, ForeignKeyMetadata... fks) {
        return new TableMetadata(name, List.of(new ColumnMetadata("id", "BIGINT", false, null, true, List.of())),
                List.of(fks), List.of(), List.of(), "id");
    }

    @Test
    void addsVerticesForAllTables() {
        TableDependencyGraph graph = new TableDependencyGraph();
        graph.build(List.of(meta("a"), meta("b")));
        assertThat(graph.graph().vertexSet()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void addsEdgeFromParentToChild() {
        TableDependencyGraph graph = new TableDependencyGraph();
        graph.build(List.of(
                meta("a"),
                meta("b", new ForeignKeyMetadata("fk", "a_id", "a", "id", true))));
        Graph<String, DefaultEdge> g = graph.graph();
        assertThat(g.containsEdge("a", "b")).isTrue();
        assertThat(graph.adjacency().get("a")).containsExactly("b");
        assertThat(graph.adjacency().get("b")).isEmpty();
    }

    @Test
    void selfReferencingFkDoesNotCreateEdge() {
        TableDependencyGraph graph = new TableDependencyGraph();
        graph.build(List.of(meta("a", new ForeignKeyMetadata("fk_self", "parent_id", "a", "id", true))));
        assertThat(graph.graph().containsEdge("a", "a")).isFalse();
        assertThat(graph.adjacency().get("a")).isEmpty();
    }

    @Test
    void fkToTableOutsideSelectionIsSkipped() {
        TableDependencyGraph graph = new TableDependencyGraph();
        graph.build(List.of(meta("child", new ForeignKeyMetadata("fk", "parent_id", "parent", "id", true))));
        assertThat(graph.graph().vertexSet()).containsExactly("child");
        assertThat(graph.graph().edgeSet()).isEmpty();
    }

    @Test
    void buildIsReusable() {
        TableDependencyGraph graph = new TableDependencyGraph();
        graph.build(List.of(meta("a")));
        graph.build(List.of(meta("x"), meta("y", new ForeignKeyMetadata("fk", "x_id", "x", "id", true))));
        assertThat(graph.graph().vertexSet()).containsExactlyInAnyOrder("x", "y");
        assertThat(graph.graph().containsEdge("x", "y")).isTrue();
    }
}