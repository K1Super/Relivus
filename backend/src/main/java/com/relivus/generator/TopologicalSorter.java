package com.relivus.generator;

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kahn 拓扑排序。
 *
 * <p>返回排序结果；存在环时返回环内顶点集合（按强连通分量分解成多个环），供引擎做
 * 「循环依赖 FK 可空校验」与两阶段插入。
 */
public final class TopologicalSorter {

    private TopologicalSorter() {
    }

    /**
     * 排序结果。
     *
     * @param hasCycle 是否存在环（自引用外键不建边，故不算环）
     * @param order    拓扑顺序；有环时仅含非环部分的合法前缀
     * @param cycles   环内顶点集合（每个 List 为一个强连通分量）
     */
    public record TopologicalSortResult(boolean hasCycle, List<String> order, List<List<String>> cycles) {
    }

    /** 基于已构建的依赖图执行 Kahn 排序并检测环。 */
    public static TopologicalSortResult sort(TableDependencyGraph dependencyGraph) {
        Graph<String, DefaultEdge> graph = dependencyGraph.graph();
        Map<String, List<String>> adjacency = dependencyGraph.adjacency();

        Map<String, Integer> inDegree = new HashMap<>();
        for (String vertex : graph.vertexSet()) {
            inDegree.put(vertex, graph.inDegreeOf(vertex));
        }

        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.offer(e.getKey());
            }
        }

        List<String> order = new ArrayList<>(graph.vertexSet().size());
        while (!queue.isEmpty()) {
            String vertex = queue.poll();
            order.add(vertex);
            for (String child : adjacency.getOrDefault(vertex, List.of())) {
                Integer next = inDegree.computeIfPresent(child, (k, v) -> v - 1);
                if (next != null && next == 0) {
                    queue.offer(child);
                }
            }
        }

        boolean hasCycle = order.size() < graph.vertexSet().size();
        List<List<String>> cycles = hasCycle ? findCycles(graph) : List.of();
        return new TopologicalSortResult(hasCycle, List.copyOf(order), cycles);
    }

    /** 环检测：强连通分量分解，SCC 顶点数 > 1 即视为环。 */
    static List<List<String>> findCycles(Graph<String, DefaultEdge> graph) {
        GabowStrongConnectivityInspector<String, DefaultEdge> gabow =
                new GabowStrongConnectivityInspector<>(graph);
        List<List<String>> cycles = new ArrayList<>();
        for (Set<String> scc : gabow.stronglyConnectedSets()) {
            if (scc.size() > 1) {
                cycles.add(new ArrayList<>(scc));
            }
        }
        return cycles;
    }
}