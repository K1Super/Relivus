import { useMemo } from 'react';
import { Alert } from 'antd';
import ReactFlow, {
  Background,
  Controls,
  MarkerType,
  type Edge,
  type Node,
  type NodeProps,
} from 'reactflow';
import 'reactflow/dist/style.css';
import type { TableDetail } from '@/types/api';
import styles from './DependencyGraph.module.css';

interface TableNodeData {
  name: string;
  columnCount: number;
  primaryKey: string | null;
  inCycle: boolean;
}

function TableNode({ data }: NodeProps<TableNodeData>) {
  return (
    <div
      className={styles.node}
      data-cyclic={data.inCycle ? 'true' : 'false'}
      aria-label={`表 ${data.name}`}
    >
      <span className={styles.nodeName}>{data.name}</span>
      <span className={styles.nodeMeta}>
        {data.primaryKey ? `PK ${data.primaryKey} · ` : ''}
        {data.columnCount} 列
      </span>
    </div>
  );
}

const nodeTypes = { table: TableNode };

interface DependencyGraphProps {
  tables: TableDetail[];
  order: string[];
  cycles: string[][];
}

/**
 * 表依赖图（React Flow 渲染，DOC-07）。
 * 按拓扑序布局（父表在前）；外键构成有向边；循环依赖表的节点高亮。
 */
export function DependencyGraph({ tables, order, cycles }: DependencyGraphProps) {
  const cycleTables = useMemo(() => new Set(cycles.flat()), [cycles]);

  const nodes: Node<TableNodeData>[] = useMemo(() => {
    const rank = new Map(order.map((name, idx) => [name, idx]));
    const cols = Math.max(1, Math.ceil(Math.sqrt(tables.length)));
    return tables.map((table, idx) => {
      const pos = rank.get(table.tableName) ?? idx;
      return {
        id: table.tableName,
        type: 'table',
        position: {
          x: (pos % cols) * 260,
          y: Math.floor(pos / cols) * 96 + 8,
        },
        data: {
          name: table.tableName,
          columnCount: table.columns.length,
          primaryKey: table.primaryKey,
          inCycle: cycleTables.has(table.tableName),
        },
      };
    });
  }, [tables, order, cycleTables]);

  const edges: Edge[] = useMemo(() => {
    const seen = new Set<string>();
    const list: Edge[] = [];
    for (const table of tables) {
      for (const fk of table.foreignKeys) {
        const key = `${fk.refTable}->${table.tableName}`;
        if (seen.has(key)) {
          continue;
        }
        seen.add(key);
        list.push({
          id: key,
          source: fk.refTable,
          target: table.tableName,
          label: fk.columnName,
          markerEnd: { type: MarkerType.ArrowClosed },
          style: cycleTables.has(table.tableName) || cycleTables.has(fk.refTable)
            ? { stroke: 'var(--color-error)', strokeWidth: 1.5 }
            : { stroke: 'var(--gray-400)' },
        });
      }
    }
    return list;
  }, [tables, cycleTables]);

  return (
    <div
      className={styles.wrapper}
      data-testid="dependency-graph"
      role="img"
      aria-label="表依赖关系图"
    >
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        nodesDraggable
        fitView
        fitViewOptions={{ padding: 0.2 }}
        minZoom={0.3}
        maxZoom={1.5}
      >
        <Background gap={16} color="var(--gray-200)" />
        <Controls />
      </ReactFlow>
      {cycles.length > 0 && (
        <Alert
          className={styles.cycleAlert}
          type="warning"
          showIcon
          message="检测到循环外键依赖"
          description={cycles
            .map(c => c.join(' → ') + ' → ' + c[0])
            .join('；')}
        />
      )}
    </div>
  );
}