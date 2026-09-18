import { useMemo, useState } from 'react';
import { Button, Empty, List, Skeleton, Tabs, Tag, Typography } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { Database, ExternalLink } from 'lucide-react';
import { useConnections } from '@/hooks/useConnections';
import { useDependencyGraph, useSchemaTables, useTableDetail } from '@/hooks/useSchema';
import { useQueries } from '@tanstack/react-query';
import { schemaApi } from '@/api/schema';
import { DependencyGraph } from '@/components/DependencyGraph';
import { ErrorState } from '@/components/ErrorState';
import { describeError, type TableDetail } from '@/types/api';

function TableDetailPanel({ connId, tableName }: { connId: number; tableName: string }) {
  const detail = useTableDetail(connId, tableName);
  if (detail.isLoading) {
    return <Skeleton active paragraph={{ rows: 6 }} />;
  }
  if (detail.isError || !detail.data) {
    return <ErrorState title="表详情加载失败" description={describeError(detail.error)} action={<Button onClick={() => detail.refetch()}>重试</Button>} />;
  }
  const table = detail.data;
  return (
    <div>
      <Typography.Title level={5} className="mono-text">
        {table.tableName}
      </Typography.Title>
      <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap' }}>
        {table.primaryKey && <Tag color="blue">PK {table.primaryKey}</Tag>}
        <Tag>{table.columns.length} 列</Tag>
        {table.foreignKeys.length > 0 && <Tag color="orange">{table.foreignKeys.length} 个外键</Tag>}
      </div>
      <table className="schema-table" aria-label={`表 ${table.tableName} 列结构`}>
        <thead>
          <tr>
            <th scope="col">列名</th>
            <th scope="col">类型</th>
            <th scope="col">可空</th>
            <th scope="col">默认值</th>
            <th scope="col">约束/说明</th>
          </tr>
        </thead>
        <tbody>
          {table.columns.map(col => {
            const flags: string[] = [];
            if (col.autoIncrement) flags.push('自增');
            if (table.primaryKey === col.columnName) flags.push('主键');
            if (col.enumValues?.length) flags.push(`ENUM(${col.enumValues.join(',')})`);
            return (
              <tr key={col.columnName}>
                <td className="mono-text">{col.columnName}</td>
                <td className="mono-text">{col.dataType}</td>
                <td>{col.nullable ? '是' : '否'}</td>
                <td className="mono-text">{col.defaultValue ?? '—'}</td>
                <td>
                  {flags.map(f => (
                    <Tag key={f} style={{ marginInlineEnd: 4 }}>
                      {f}
                    </Tag>
                  ))}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {table.foreignKeys.length > 0 && (
        <div style={{ marginTop: 16 }}>
          <Typography.Text strong style={{ fontSize: 13 }}>
            外键
          </Typography.Text>
          <ul style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 4 }}>
            {table.foreignKeys.map(fk => (
              <li key={fk.fkName} className="mono-text" style={{ fontSize: 13 }}>
                {fk.columnName} → {fk.refTable}.{fk.refColumn}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

/** Schema 浏览页（/schema、/schema/:connId）：表结构 + React Flow 依赖图。 */
export default function SchemaPage() {
  const { connId: rawId } = useParams();
  const connId = rawId ? Number(rawId) : undefined;
  const navigate = useNavigate();
  const connections = useConnections();
  const tables = useSchemaTables(connId);
  const dependencies = useDependencyGraph(connId);
  const [activeTable, setActiveTable] = useState<string | undefined>();

  // 为依赖图拉取全部表详情（外键信息来自各表详情）
  const detailQueries = useQueries({
    queries:
      tables.data?.map(t => ({
        queryKey: ['schema', connId, 'table', t.tableName] as const,
        queryFn: () => schemaApi.table(connId as number, t.tableName),
        enabled: connId != null,
      })) ?? [],
  });
  const tableDetails = useMemo(
    () => detailQueries.map(q => q.data).filter((d): d is TableDetail => d != null),
    [detailQueries],
  );

  // 未指定连接 → 连接选择器
  if (connId == null) {
    if (connections.isLoading) {
      return <Skeleton active paragraph={{ rows: 8 }} />;
    }
    if (connections.isError) {
      return <ErrorState title="连接列表加载失败" description={describeError(connections.error)} action={<Button onClick={() => connections.refetch()}>重试</Button>} />;
    }
    if ((connections.data?.length ?? 0) === 0) {
      return (
        <div className="page-header">
          <div>
            <h1 className="page-title">Schema 浏览</h1>
            <p className="page-subtitle">暂无连接，请先到「连接管理」创建。</p>
          </div>
        </div>
      );
    }
    return (
      <div>
        <div className="page-header">
          <div>
            <h1 className="page-title">Schema 浏览</h1>
            <p className="page-subtitle">选择一个连接查看表结构与依赖关系</p>
          </div>
        </div>
        <div className="section-card">
          <List
            dataSource={connections.data ?? []}
            renderItem={conn => (
              <List.Item
                actions={[
                  <Button key="open" type="link" onClick={() => navigate(`/schema/${conn.id}`)}>
                    浏览 <ExternalLink size={14} />
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  avatar={<Database size={20} color="var(--gray-500)" />}
                  title={<Typography.Text strong>{conn.name}</Typography.Text>}
                  description={
                    <span className="mono-text">
                      {conn.dbType} · {conn.host}:{conn.port}/{conn.database}
                    </span>
                  }
                />
              </List.Item>
            )}
            locale={{ emptyText: <Empty description="暂无连接" /> }}
          />
        </div>
      </div>
    );
  }

  const active = activeTable ?? tables.data?.[0]?.tableName;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Schema 浏览</h1>
          <p className="page-subtitle">连接 ID：{connId}（元数据缓存 10 分钟）</p>
        </div>
        <Button onClick={() => navigate('/connections')}>返回连接</Button>
      </div>

      {tables.isLoading ? (
        <Skeleton active paragraph={{ rows: 10 }} />
      ) : tables.isError ? (
        <ErrorState title="Schema 加载失败" description={describeError(tables.error)} action={<Button onClick={() => tables.refetch()}>重试</Button>} />
      ) : (tables.data?.length ?? 0) === 0 ? (
        <div className="section-card">
          <Empty description="该连接下没有可浏览的表" />
        </div>
      ) : (
        <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
          <div className="section-card" style={{ width: 240, flexShrink: 0 }}>
            <Typography.Text strong style={{ fontSize: 13 }}>
              表（{tables.data?.length}）
            </Typography.Text>
            <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 4 }}>
              {(tables.data ?? []).map(t => (
                <button
                  key={t.tableName}
                  type="button"
                  className={`table-item ${active === t.tableName ? 'table-item--active' : ''}`}
                  onClick={() => setActiveTable(t.tableName)}
                >
                  <span className="mono-text">{t.tableName}</span>
                  <span className="num-cell" style={{ fontSize: 12, color: 'var(--gray-500)' }}>
                    {t.columnCount}
                  </span>
                </button>
              ))}
            </div>
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="section-card">
              <Tabs
                items={[
                  {
                    key: 'detail',
                    label: '表结构',
                    children: active ? <TableDetailPanel connId={connId} tableName={active} /> : <Empty description="请选择表" />,
                  },
                  {
                    key: 'graph',
                    label: '依赖图',
                    children: dependencies.isError ? (
                      <ErrorState title="依赖图加载失败" description={describeError(dependencies.error)} action={<Button onClick={() => dependencies.refetch()}>重试</Button>} />
                    ) : tableDetails.length === 0 ? (
                      <Empty description="暂无表数据" />
                    ) : (
                      <DependencyGraph tables={tableDetails} order={dependencies.data?.order ?? []} cycles={dependencies.data?.cycles ?? []} />
                    ),
                  },
                ]}
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}