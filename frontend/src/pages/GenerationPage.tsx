import { useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Checkbox,
  Form,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Play, SlidersHorizontal, Wand2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useConnections } from '@/hooks/useConnections';
import { useSchemaTables, useTableDetail } from '@/hooks/useSchema';
import { generationApi } from '@/api/generation';
import { ColumnConfigForm } from '@/components/ColumnConfigForm';
import { ErrorState } from '@/components/ErrorState';
import { describeError, type GenerationConfig, type GenerationColumnConfig, type GenerationRunResult } from '@/types/api';

type SelectedState = Record<string, boolean>;
type RowCountState = Record<string, number>;
type ColumnConfigState = Record<string, Record<string, GenerationColumnConfig>>;

/** 数据生成页：表选择、行数、列生成器、采样策略、预览与执行。 */
export default function GenerationPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const connections = useConnections();

  const [connectionId, setConnectionId] = useState<number>();
  const tables = useSchemaTables(connectionId);
  const [selected, setSelected] = useState<SelectedState>({});
  const [rowCounts, setRowCounts] = useState<RowCountState>({});
  const [columnConfigs, setColumnConfigs] = useState<ColumnConfigState>({});
  const [configTable, setConfigTable] = useState<string>();
  const [samplingStrategy, setSamplingStrategy] = useState<GenerationConfig['samplingStrategy']>('UNIFORM');
  const [truncateBefore, setTruncateBefore] = useState(false);
  const [batchSize, setBatchSize] = useState(1000);
  const [previewResult, setPreviewResult] = useState<GenerationRunResult>();

  const configDetail = useTableDetail(connectionId, configTable);

  // 切换连接时重置表级状态
  useEffect(() => {
    setSelected({});
    setRowCounts({});
    setColumnConfigs({});
    setConfigTable(undefined);
    setPreviewResult(undefined);
  }, [connectionId]);

  const toggleTable = (table: string, on: boolean) => {
    setSelected(prev => ({ ...prev, [table]: on }));
    if (on && rowCounts[table] == null) {
      setRowCounts(prev => ({ ...prev, [table]: 100 }));
    }
  };

  const selectedTables = useMemo(
    () =>
      (tables.data ?? [])
        .filter(t => selected[t.tableName])
        .map(t => ({
          table: t.tableName,
          rowCount: rowCounts[t.tableName] ?? 100,
        })),
    [tables.data, selected, rowCounts],
  );

  const buildConfig = (): GenerationConfig => ({
    connectionId: connectionId as number,
    tables: selectedTables.map(t => ({
      table: t.table,
      rowCount: t.rowCount,
      ...(columnConfigs[t.table] && Object.keys(columnConfigs[t.table]).length > 0
        ? { columns: columnConfigs[t.table] }
        : {}),
    })),
    samplingStrategy,
    truncateBefore,
    batchSize,
  });

  const canSubmit = connectionId != null && selectedTables.length > 0;

  const runPreview = async () => {
    if (!canSubmit) {
      message.warning('请先选择连接与至少一张表');
      return;
    }
    try {
      const result = await generationApi.preview(buildConfig());
      setPreviewResult(result);
    } catch (err) {
      message.error(describeError(err));
    }
  };

  const runExecute = async () => {
    if (!canSubmit) {
      message.warning('请先选择连接与至少一张表');
      return;
    }
    try {
      const task = await generationApi.execute(buildConfig());
      message.success(`任务已创建（#${task.id}）`);
      navigate(`/tasks?focus=${task.id}`);
    } catch (err) {
      message.error(describeError(err));
    }
  };

  const columns: ColumnsType<{
    table: string;
    columnCount: number;
    primaryKey: string | null;
  }> = [
    {
      title: '启用',
      key: 'enabled',
      width: 64,
      render: (_, row) => (
        <Checkbox
          checked={Boolean(selected[row.table])}
          aria-label={`选择表 ${row.table}`}
          onChange={e => toggleTable(row.table, e.target.checked)}
        />
      ),
    },
    {
      title: '表名',
      dataIndex: 'table',
      render: (name: string, row) => (
        <>
          <span className="mono-text">{name}</span>
          {row.primaryKey && <Tag style={{ marginLeft: 8 }} color="blue">PK {row.primaryKey}</Tag>}
        </>
      ),
    },
    { title: '列数', dataIndex: 'columnCount', width: 96, className: 'num-cell' },
    {
      title: '生成行数',
      key: 'rows',
      width: 140,
      render: (_, row) => (
        <InputNumber
          min={1}
          max={500_000}
          disabled={!selected[row.table]}
          value={rowCounts[row.table] ?? 100}
          aria-label={`表 ${row.table} 生成行数`}
          onChange={v => setRowCounts(prev => ({ ...prev, [row.table]: v ?? 100 }))}
        />
      ),
    },
    {
      title: '操作',
      key: 'config',
      width: 120,
      render: (_, row) => (
        <Button
          size="small"
          icon={<SlidersHorizontal size={14} />}
          disabled={!selected[row.table]}
          onClick={() => setConfigTable(row.table)}
        >
          列配置
        </Button>
      ),
    },
  ];

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">数据生成</h1>
          <p className="page-subtitle">按表生成模拟数据；外键关系与唯一约束自动保障，预览会真实写入少量行（每表 ≤ 5 行）。</p>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <div className="section-card" style={{ flex: '1 1 480px' }}>
          <div className="section-title">1. 选择连接与表</div>
          {connections.isLoading ? (
            <Typography.Text type="secondary">加载连接中…</Typography.Text>
          ) : connections.isError ? (
            <ErrorState title="连接加载失败" description={describeError(connections.error)} action={<Button onClick={() => connections.refetch()}>重试</Button>} />
          ) : (connections.data?.length ?? 0) === 0 ? (
            <Typography.Text type="secondary">暂无连接，请先到「连接管理」创建。</Typography.Text>
          ) : (
            <Select
              aria-label="选择连接"
              style={{ width: '100%', marginBottom: 16 }}
              placeholder="选择目标库连接"
              value={connectionId}
              onChange={setConnectionId}
              options={(connections.data ?? []).map(c => ({
                value: c.id,
                label: `${c.name}（${c.host}:${c.port}/${c.database}）`,
              }))}
            />
          )}
          {tables.isLoading ? (
            <Typography.Text type="secondary">加载表结构…</Typography.Text>
          ) : tables.isError ? (
            <ErrorState title="表结构加载失败" description={describeError(tables.error)} action={<Button onClick={() => tables.refetch()}>重试</Button>} />
          ) : (
            <Table
              rowKey="table"
              size="small"
              columns={columns}
              dataSource={(tables.data ?? []).map(t => ({
                table: t.tableName,
                columnCount: t.columnCount,
                primaryKey: t.primaryKey,
              }))}
              pagination={false}
              locale={{ emptyText: '请先选择连接' }}
            />
          )}
        </div>

        <div className="section-card" style={{ flex: '1 1 320px', minWidth: 320 }}>
          <div className="section-title">2. 生成参数</div>
          <Form layout="vertical" style={{ maxWidth: 400 }}>
            <Form.Item label="外键采样策略">
              <Select
                aria-label="采样策略"
                value={samplingStrategy}
                onChange={setSamplingStrategy}
                options={[
                  { value: 'UNIFORM', label: 'UNIFORM 均匀采样' },
                  { value: 'ZIPF', label: 'ZIPF 热门倾斜' },
                ]}
              />
            </Form.Item>
            <Form.Item label="生成前清空目标表">
              <Switch
                aria-label="truncate"
                checked={truncateBefore}
                onChange={setTruncateBefore}
                checkedChildren="清空"
                unCheckedChildren="追加"
              />
              <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginTop: 4, marginBottom: 0 }}>
                清空会先删除目标表全部数据，请谨慎操作并先在预览确认。
              </Typography.Paragraph>
            </Form.Item>
            <Form.Item label="批大小">
              <InputNumber
                aria-label="批大小"
                min={1}
                max={100000}
                value={batchSize}
                onChange={v => setBatchSize(v ?? 1000)}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </Form>
          <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
            <Button icon={<Play size={16} />} onClick={runPreview} disabled={!canSubmit}>
              预览（写入 ≤5 行/表）
            </Button>
            <Button type="primary" icon={<Wand2 size={16} />} onClick={runExecute} disabled={!canSubmit}>
              执行生成
            </Button>
          </div>
        </div>
      </div>

      <Modal
        title={`列配置：${configTable ?? ''}`}
        open={configTable != null}
        onCancel={() => setConfigTable(undefined)}
        footer={[
          <Button key="close" onClick={() => setConfigTable(undefined)}>
            完成
          </Button>,
        ]}
        width={720}
        destroyOnHidden
      >
        {configDetail.isError ? (
          <ErrorState title="表详情加载失败" description={describeError(configDetail.error)} />
        ) : configDetail.isLoading || !configDetail.data ? (
          <Typography.Text type="secondary">加载表详情…</Typography.Text>
        ) : (
          <ColumnConfigForm
            detail={configDetail.data}
            value={columnConfigs[configTable as string] ?? {}}
            onChange={next => setColumnConfigs(prev => ({ ...prev, [configTable as string]: next }))}
          />
        )}
      </Modal>

      <Modal
        title="预览结果（真实写入目标表）"
        open={previewResult != null}
        onCancel={() => setPreviewResult(undefined)}
        footer={[
          <Button key="close" onClick={() => setPreviewResult(undefined)}>
            关闭
          </Button>,
        ]}
        destroyOnHidden
      >
        {previewResult && (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Typography.Text type="secondary">
              总耗时 {(previewResult.elapsedMillis / 1000).toFixed(2)}s
            </Typography.Text>
            {Object.entries(previewResult.rowsByTable).map(([table, rows]) => (
              <div key={table} style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span className="mono-text">{table}</span>
                <span className="num-cell">{rows} 行</span>
              </div>
            ))}
          </Space>
        )}
      </Modal>
    </div>
  );
}