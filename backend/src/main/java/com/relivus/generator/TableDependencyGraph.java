package com.relivus.generator;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedPseudograph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表依赖图（DOC-03，基于 JGraphT）。
 *
 * <p>顶点为表名；外键 parent → child 建边；自引用外键不加入边集。
 * 提供图、拓扑排序结果与强连通循环检测。
 */
public class TableDependencyGraph {

    private final Graph<String, DefaultEdge> graph = new DirectedPseudograph<>(DefaultEdge.class);
    private final Map<String, List<String>> adjacency = new LinkedHashMap<>();

    /**
     * 构建依赖图。
     *
     * @param tables 全部参与生成的表元数据
     * @return 顶点与边已填充的图
     */
    public Graph<String, DefaultEdge> build(List<com.relivus.schema.model.TableMetadata> tables) {
        graph.removeAllVertices(graph.vertexSet());
        adjacency.clear();
        for (com.relivus.schema.model.TableMetadata table : tables) {
            graph.addVertex(table.tableName());
            adjacency.computeIfAbsent(table.tableName(), k -> new ArrayList<>());
        }
        for (com.relivus.schema.model.TableMetadata table : tables) {
            for (com.relivus.schema.model.ForeignKeyMetadata fk : table.foreignKeys()) {
                String parent = fk.refTable();
                String child = table.tableName();
                if (parent.equals(child)) {
                    continue; // 自引用外键不加入边集
                }
                if (graph.containsVertex(parent)) {
                    graph.addEdge(parent, child);
                    adjacency.get(parent).add(child);
                }
            }
        }
        return graph;
    }

    /** 底层 JGraphT 图。 */
    public Graph<String, DefaultEdge> graph() {
        return graph;
    }

    /** 邻接表（parent → children），供 Kahn 排序复用。 */
    public Map<String, List<String>> adjacency() {
        return adjacency;
    }
}