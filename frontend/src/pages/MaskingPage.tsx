import { useEffect, useState } from 'react';
import {
  App,
  Button,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Table,
  Tag,
  Typography,
  Alert,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ShieldCheck, ShieldPlus, SlidersHorizontal } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useConnections } from '@/hooks/useConnections';
import { useSchemaTables, useTableDetail } from '@/hooks/useSchema';
import { maskingApi } from '@/api/masking';
import { MaskingRuleForm } from '@/components/MaskingRuleForm';
import { ErrorState } from '@/components/ErrorState';
import {
  describeError,
  type JoinVerificationResult,
  type MaskingPreviewResponse,
  type MaskingTableRule,
  type MaskingTaskRequest,
  type PreviewRow,
  type VerifyTableSpec,
} from '@/types/api';

type RuleState = Record<string, MaskingTableRule>;

/** 数据脱敏页：敏感列规则、映射分组、JOIN 一致性验证（DOC-07 / DOC-04）。 */
export default function MaskingPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const connections = useConnections();

  const [connectionId, setConnectionId] = useState<number>();
  const tables = useSchemaTables(connectionId);
  const [rules, setRules] = useState<RuleState>({});
  const [ruleTable, setRuleTable] = useState<string>();
  const [verifyTables, setVerifyTables] = useState<VerifyTableSpec[]>([]);
  const [verifyTable, setVerifyTable] = useState<string>();
  const [pkColumn, setPkColumn] = useState('');
  const [joinKeyColumn, setJoinKeyColumn] = useState('');
  const [batchSize, setBatchSize] = useState(1000);
  const [joinSql, setJoinSql] = useState('');
  const [preview, setPreview] = useState<MaskingPreviewResponse>();
  const [verifyResult, setVerifyResult] = useState<JoinVerificationResult>();

  const ruleDetail = useTableDetail(connectionId, ruleTable);

  useEffect(() => {
    setRules({});
    setVerifyTables([]);
    setVerifyTable(undefined);
    setPreview(undefined);
    setVerifyResult(undefined);
  }, [connectionId]);

  const toggleTable = (table: string, on: boolean) => {
    setRules(prev => {
      const next = { ...prev };
      if (on) {
        next[table] = { table, columns: {} };
      } else {
        delete next[table];
      }
      return next;
    });
  };

  const buildRequest = (): MaskingTaskRequest => ({
    connectionId: connectionId as number,
    tables: Object.values(rules),
    batchSize,
    ...(verifyTables.length > 0 ? { verifyTables } : {}),
  });

  const canSubmit = connectionId != null && Object.keys(rules).length > 0;

  const runPreview = async () => {
    try {
      setPreview(await maskingApi.preview(buildRequest()));
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
      const task = await maskingApi.execute(buildRequest());
      message.success(`脱敏任务已创建（#${task.id}）`);
      navigate(`/tasks?focus=${task.id}`);
    } catch (err) {
      message.error(describeError(err));
    }
  };

  const addVerifyTable = () => {
    if (!verifyTable || !pkColumn || !joinKeyColumn) {
      message.warning('请完整填写验证目标：表、主键列、JOIN 键列');
      return;
    }
    setVerifyTables(prev => [
      ...prev,
      { table: verifyTable, pkColumn, joinKeyColumn },
    ]);
    setVerifyTable(undefined);
    setPkColumn('');
    setJoinKeyColumn('');
  };

  const runVerify = async () => {
    if (verifyTables.length === 0) {
      message.warning('请先添加 JOIN 一致性验证目标');
      return;
    }
    if (!joinSql.trim()) {
      message.warning('请填写 JOIN 查询（用 ? 作为抽样主键占位符）');
      return;
    }
    const spec = verifyTables[0];
    try {
      const result = await maskingApi.verify({
        connectionId: connectionId as number,
        joinSql: joinSql.trim(),
        targetTable: spec.table,
        pkColumn: spec.pkColumn,
        joinKeyColumn: spec.joinKeyColumn,
      });
      setVerifyResult(result);
    } catch (err) {
      message.error(describeError(err));
    }
  };

  const columns: ColumnsType<{ table: string; columnCount: number }> = [
    {
      title: '启用',
      key: 'enabled',
      width: 64,
      render: (_, row) => (
        <Checkbox
          checked={Boolean(rules[row.table])}
          aria-label={`选择表 ${row.table}`}
          onChange={e => toggleTable(row.table, e.target.checked)}
        />
      ),
    },
    {
      title: '表名',
      dataIndex: 'table',
      render: (name: string) => <span className="mono-text">{name}</span>,
    },
    { title: '列数', dataIndex: 'columnCount', width: 96, className: 'num-cell' },
    {
      title: '规则',
      key: 'rules',
      render: (_, row) => {
        const rule = rules[row.table];
        const count = rule ? Object.keys(rule.columns).length : 0;
        return count > 0 ? <Tag color="orange">{count} 列</Tag> : <Tag>未配置</Tag>;
      },
    },
    {
      title: '操作',
      key: 'config',
      width: 120,
      render: (_, row) => (
        <Button
          size="small"
          icon={<SlidersHorizontal size={14} />}
          disabled={!rules[row.table]}
          onClick={() => setRuleTable(row.table)}
        >
          规则配置
        </Button>
      ),
    },
  ];

  const previewRows: PreviewRow[] = preview?.rows ?? [];

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">数据脱敏</h1>
          <p className="page-subtitle">对敏感列脱敏；同映射分组跨表结果一致，可配置脱敏前快照验证 JOIN 一致性（快照仅保留到验证消费）。</p>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <div className="section-card" style={{ flex: '1 1 520px' }}>
          <div className="section-title">1. 选择表并配置敏感列</div>
          {connections.isLoading ? (
            <Typography.Text type="secondary">加载连接中…</Typography.Text>
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
              dataSource={(tables.data ?? []).map(t => ({ table: t.tableName, columnCount: t.columnCount }))}
              pagination={false}
              locale={{ emptyText: '请先选择连接' }}
            />
          )}
        </div>

        <div style={{ flex: '1 1 300px', minWidth: 300 }}>
          <div className="section-card">
            <div className="section-title">2. 执行参数与验证目标</div>
            <Form layout="vertical" style={{ maxWidth: 380 }}>
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
              <Form.Item label="JOIN 一致性验证目标（脱敏前自动建快照）">
                <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                  <Select
                    aria-label="验证目标表"
                    style={{ flex: 1 }}
                    placeholder="表"
                    value={verifyTable}
                    onChange={setVerifyTable}
                    options={Object.keys(rules).map(t => ({ value: t, label: t }))}
                  />
                  <Button icon={<ShieldPlus size={14} />} onClick={addVerifyTable}>
                    添加
                  </Button>
                </div>
                <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                  <Input
                    aria-label="主键列"
                    placeholder="主键列，如 id"
                    value={pkColumn}
                    onChange={e => setPkColumn(e.target.value)}
                  />
                  <Input
                    aria-label="JOIN 键列"
                    placeholder="JOIN 键列（对比列）"
                    value={joinKeyColumn}
                    onChange={e => setJoinKeyColumn(e.target.value)}
                  />
                </div>
                {verifyTables.map(spec => (
                  <Tag
                    key={spec.table}
                    closable
                    onClose={() => setVerifyTables(prev => prev.filter(v => v.table !== spec.table))}
                  >
                    {spec.table}（PK {spec.pkColumn} / JOIN {spec.joinKeyColumn}）
                  </Tag>
                ))}
              </Form.Item>
            </Form>
            <div style={{ display: 'flex', gap: 8 }}>
              <Button icon={<SlidersHorizontal size={16} />} onClick={runPreview} disabled={!canSubmit}>
                预览脱敏
              </Button>
              <Button type="primary" icon={<ShieldCheck size={16} />} onClick={runExecute} disabled={!canSubmit}>
                执行脱敏
              </Button>
            </div>
          </div>

          <div className="section-card">
            <div className="section-title">3. 验证 JOIN 一致性（脱敏执行完成后）</div>
            <Input.TextArea
              aria-label="JOIN 查询"
              style={{ fontFamily: 'var(--font-mono)' }}
              rows={3}
              placeholder={'JOIN 查询，用 ? 作主键占位，如：\nSELECT o.id, o.customer_name FROM orders o JOIN customers c ON o.customer_name = c.name WHERE c.id = ?'}
              value={joinSql}
              onChange={e => setJoinSql(e.target.value)}
            />
            <Button style={{ marginTop: 8 }} onClick={runVerify} disabled={verifyTables.length === 0}>
              验证一致性
            </Button>
            {verifyResult && (
              <Alert
                style={{ marginTop: 12 }}
                type={verifyResult.consistent ? 'success' : 'error'}
                showIcon
                message={verifyResult.consistent ? '验证通过' : '验证失败'}
                description={verifyResult.message}
              />
            )}
          </div>
        </div>
      </div>

      <Modal
        title={`脱敏规则：${ruleTable ?? ''}`}
        open={ruleTable != null}
        onCancel={() => setRuleTable(undefined)}
        footer={[
          <Button key="close" onClick={() => setRuleTable(undefined)}>
            完成
          </Button>,
        ]}
        width={760}
        destroyOnHidden
      >
        {ruleDetail.isError ? (
          <ErrorState title="表详情加载失败" description={describeError(ruleDetail.error)} />
        ) : ruleDetail.isLoading || !ruleDetail.data ? (
          <Typography.Text type="secondary">加载表详情…</Typography.Text>
        ) : (
          <MaskingRuleForm
            detail={ruleDetail.data}
            value={rules[ruleTable as string]}
            onChange={next => setRules(prev => ({ ...prev, [ruleTable as string]: next }))}
          />
        )}
      </Modal>

      <Modal
        title="脱敏预览（不落库）"
        open={preview != null}
        onCancel={() => setPreview(undefined)}
        footer={[
          <Button key="close" onClick={() => setPreview(undefined)}>
            关闭
          </Button>,
        ]}
        width={640}
        destroyOnHidden
      >
        <Table<PreviewRow>
          rowKey={(row, idx) => `${row.table}-${row.column}-${idx}`}
          size="small"
          dataSource={previewRows.slice(0, 100)}
          pagination={{ pageSize: 20 }}
          columns={[
            { title: '表', dataIndex: 'table', render: (t: string) => <span className="mono-text">{t}</span> },
            { title: '列', dataIndex: 'column', render: (c: string) => <span className="mono-text">{c}</span> },
            { title: '原始值', dataIndex: 'original' },
            { title: '脱敏后', dataIndex: 'masked', render: (m: string) => <span style={{ color: 'var(--color-primary)' }}>{m}</span> },
          ]}
        />
        {previewRows.length === 0 && <Typography.Text type="secondary">无预览数据</Typography.Text>}
      </Modal>
    </div>
  );
}