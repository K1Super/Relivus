package com.relivus.generator;

import com.relivus.schema.model.ColumnMetadata;
import com.relivus.schema.model.ForeignKeyMetadata;
import com.relivus.schema.model.TableMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 拓扑排序测试（DOC-08：无环、有环、自引用）。
 */
class TopologicalSorterTest {

    private static TableMetadata meta(String name, ForeignKeyMetadata... fks) {
        return new TableMetadata(name, List.of(new ColumnMetadata("id", "BIGINT", false, null, true, List.of())),
                List.of(fks), List.of(), List.of(), "id");
    }

    private static TableDependencyGraph build(TableMetadata... tables) {
        TableDependencyGraph graph = new TableDependencyGraph();
        graph.build(List.of(tables));
        return graph;
    }

    @Test
    void acyclicGraphProducesFullOrder() {
        TableMetadata a = meta("a");
        TableMetadata b = meta("b", new ForeignKeyMetadata("fk_b", "a_id", "a", "id", true));
        TopologicalSorter.TopologicalSortResult result = TopologicalSorter.sort(build(a, b));

        assertThat(result.hasCycle()).isFalse();
        assertThat(result.cycles()).isEmpty();
        assertThat(result.order()).containsExactly("a", "b");
    }

    @Test
    void multiLevelDependenciesOrderRespected() {
        TableMetadata a = meta("a");
        TableMetadata b = meta("b", new ForeignKeyMetadata("fk", "a_id", "a", "id", true));
        TableMetadata c = meta("c", new ForeignKeyMetadata("fk", "b_id", "b", "id", true));
        TopologicalSorter.TopologicalSortResult result = TopologicalSorter.sort(build(a, b, c));

        assertThat(result.order()).containsExactly("a", "b", "c");
    }

    @Test
    void selfReferencingFkIsNotACycle() {
        TableMetadata a = meta("a", new ForeignKeyMetadata("fk_self", "parent_id", "a", "id", true));
        TopologicalSorter.TopologicalSortResult result = TopologicalSorter.sort(build(a));

        assertThat(result.hasCycle()).isFalse();
        assertThat(result.order()).containsExactly("a");
    }

    @Test
    void cycleIsDetectedAndReported() {
        TableMetadata a = meta("a", new ForeignKeyMetadata("fk_a", "b_id", "b", "id", true));
        TableMetadata b = meta("b", new ForeignKeyMetadata("fk_b", "a_id", "a", "id", true));
        TopologicalSorter.TopologicalSortResult result = TopologicalSorter.sort(build(a, b));

        assertThat(result.hasCycle()).isTrue();
        assertThat(result.order()).hasSizeLessThan(2);
        assertThat(result.cycles()).hasSize(1);
        assertThat(result.cycles().get(0)).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void multipleIndependentCycles() {
        TableMetadata a = meta("a", new ForeignKeyMetadata("fk", "b_id", "b", "id", true));
        TableMetadata b = meta("b", new ForeignKeyMetadata("fk", "a_id", "a", "id", true));
        TableMetadata c = meta("c", new ForeignKeyMetadata("fk", "d_id", "d", "id", true));
        TableMetadata d = meta("d", new ForeignKeyMetadata("fk", "c_id", "c", "id", true));
        TopologicalSorter.TopologicalSortResult result = TopologicalSorter.sort(build(a, b, c, d));

        assertThat(result.hasCycle()).isTrue();
        assertThat(result.cycles()).hasSize(2);
        assertThat(result.cycles().stream().flatMap(List::stream))
                .containsExactlyInAnyOrder("a", "b", "c", "d");
    }
}