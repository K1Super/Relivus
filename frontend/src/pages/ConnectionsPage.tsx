import { useState } from 'react';
import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Pencil, Plus, Trash2, Zap } from 'lucide-react';
import { Link } from 'react-router-dom';
import {
  useConnections,
  useCreateConnection,
  useDeleteConnection,
  useTestConnection,
  useUpdateConnection,
} from '@/hooks/useConnections';
import { EmptyState } from '@/components/EmptyState';
import { ErrorState } from '@/components/ErrorState';
import { describeError, type ConnectionResponse, type DbType } from '@/types/api';

interface FormValues {
  name: string;
  dbType: DbType;
  host: string;
  port: number;
  database: string;
  username: string;
  password?: string;
  updatePassword?: boolean;
}

const DB_LABEL: Record<string, string> = { mysql: 'MySQL', postgresql: 'PostgreSQL' };

/** 连接管理页：列表、新建/编辑、测试、删除。 */
export default function ConnectionsPage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ConnectionResponse | null>(null);
  const [testResult, setTestResult] = useState<{ id: number; success: boolean; message: string; product: string; version: string } | null>(null);

  const connections = useConnections();
  const createMutation = useCreateConnection();
  const updateMutation = useUpdateConnection();
  const deleteMutation = useDeleteConnection();
  const testMutation = useTestConnection();

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ dbType: 'mysql', port: 3306, updatePassword: false });
    setModalOpen(true);
  };

  const openEdit = (conn: ConnectionResponse) => {
    setEditing(conn);
    form.resetFields();
    form.setFieldsValue({
      name: conn.name,
      dbType: conn.dbType,
      host: conn.host,
      port: conn.port,
      database: conn.database,
      username: conn.username,
      updatePassword: false,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const payload: Parameters<typeof createMutation.mutateAsync>[0] = {
      name: values.name,
      dbType: values.dbType,
      host: values.host,
      port: values.port,
      database: values.database,
      username: values.username,
    };
    try {
      if (editing) {
        const hasNewPassword = Boolean(values.password?.trim());
        if (hasNewPassword) {
          payload.password = values.password;
          payload.updatePassword = true;
        } else {
          payload.updatePassword = false;
        }
        await updateMutation.mutateAsync({ id: editing.id, req: payload });
        message.success('连接已更新');
      } else {
        payload.password = values.password;
        await createMutation.mutateAsync(payload);
        message.success('连接已创建');
      }
      setModalOpen(false);
    } catch (err) {
      message.error(describeError(err));
    }
  };

  const runTest = async (id: number) => {
    try {
      const result = await testMutation.mutateAsync(id);
      setTestResult({ id, ...result });
    } catch (err) {
      message.error(describeError(err));
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteMutation.mutateAsync(id);
      message.success('连接已删除');
    } catch (err) {
      message.error(describeError(err));
    }
  };

  const columns: ColumnsType<ConnectionResponse> = [
    {
      title: '名称',
      dataIndex: 'name',
      render: (name: string, row) => (
        <Link to={`/schema/${row.id}`}>
          <Typography.Text strong>{name}</Typography.Text>
        </Link>
      ),
    },
    {
      title: '类型',
      dataIndex: 'dbType',
      width: 120,
      render: (t: DbType) => DB_LABEL[t] ?? t,
    },
    {
      title: '地址',
      key: 'addr',
      render: (_, row) => (
        <span className="mono-text">
          {row.host}:{row.port}
        </span>
      ),
    },
    { title: '数据库', dataIndex: 'database', width: 160 },
    { title: '用户名', dataIndex: 'username', width: 120 },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, row) => (
        <Space size={4}>
          <Button size="small" icon={<Zap size={14} />} loading={testMutation.isPending} onClick={() => runTest(row.id)}>
            测试
          </Button>
          <Button size="small" icon={<Pencil size={14} />} onClick={() => openEdit(row)}>
            编辑
          </Button>
          <Popconfirm
            title="删除连接"
            description={`确定删除「${row.name}」？此操作不可撤销。`}
            okText="删除"
            okButtonProps={{ danger: true }}
            onConfirm={() => handleDelete(row.id)}
          >
            <Button danger size="small" icon={<Trash2 size={14} />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">连接管理</h1>
          <p className="page-subtitle">管理目标库连接，供 Schema 浏览、数据生成与脱敏使用。密码加密存储，不回传前端。</p>
        </div>
        {(connections.data?.length ?? 0) > 0 && (
          <Button type="primary" icon={<Plus size={16} />} onClick={openCreate}>
            新建连接
          </Button>
        )}
      </div>

      <div className="section-card">
        {connections.isLoading ? (
          <Table<ConnectionResponse> rowKey="id" columns={columns} dataSource={[]} loading pagination={false} />
        ) : connections.isError ? (
          <ErrorState
            title="连接列表加载失败"
            description={describeError(connections.error)}
            action={
              <Button onClick={() => connections.refetch()}>重试</Button>
            }
          />
        ) : (connections.data?.length ?? 0) === 0 ? (
          <EmptyState
            title="还没有连接"
            description="点击下方按钮创建第一个数据库连接"
            action={
              <Button type="primary" icon={<Plus size={16} />} onClick={openCreate}>
                新建连接
              </Button>
            }
          />
        ) : (
          <Table<ConnectionResponse>
            rowKey="id"
            columns={columns}
            dataSource={connections.data}
            pagination={{ pageSize: 20, showSizeChanger: true, pageSizeOptions: [20, 50, 100] }}
          />
        )}
      </div>

      <Modal
        title={editing ? `编辑连接：${editing.name}` : '新建连接'}
        open={modalOpen}
        onOk={submit}
        onCancel={() => setModalOpen(false)}
        okText={editing ? '保存' : '创建'}
        width={560}
        destroyOnHidden
        confirmLoading={createMutation.isPending || updateMutation.isPending}
      >
        <Form form={form} layout="vertical" requiredMark="optional" style={{ marginTop: 8 }}>
          <Form.Item name="name" label="连接名称" rules={[{ required: true, message: '请输入连接名称' }]}>
            <Input placeholder="例如：生产订单库" maxLength={128} />
          </Form.Item>
          <Space.Compact style={{ width: '100%', gap: 0 }}>
            <Form.Item name="dbType" label="数据库类型" rules={[{ required: true }]} style={{ width: '45%' }}>
              <Select
                options={[
                  { value: 'mysql', label: 'MySQL' },
                  { value: 'postgresql', label: 'PostgreSQL' },
                ]}
                onChange={(type: DbType) => form.setFieldValue('port', type === 'mysql' ? 3306 : 5432)}
              />
            </Form.Item>
            <Form.Item name="port" label="端口" rules={[{ required: true, message: '请输入端口' }]} style={{ width: '20%', flexGrow: 1 }}>
              <InputNumber min={1} max={65535} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="host" label="主机地址" rules={[{ required: true, message: '请输入主机' }]} style={{ width: '35%', flexGrow: 1 }}>
              <Input placeholder="127.0.0.1" />
            </Form.Item>
          </Space.Compact>
          <Form.Item name="database" label="数据库名" rules={[{ required: true, message: '请输入数据库名' }]}>
            <Input placeholder="relivus" />
          </Form.Item>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input placeholder="root" />
          </Form.Item>
          <Form.Item
            name="password"
            label={editing ? '密码（编辑时如需重设请填写）' : '密码'}
            rules={editing ? [] : [{ required: true, message: '请输入密码' }]}
          >
            <Input.Password placeholder={editing ? '留空保持不变' : '数据库密码'} maxLength={1024} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="连接测试结果"
        open={testResult != null}
        onCancel={() => setTestResult(null)}
        footer={[
          <Button key="close" onClick={() => setTestResult(null)}>
            关闭
          </Button>,
        ]}
      >
        {testResult && (
          <Space direction="vertical" size={8}>
            <Typography.Text type={testResult.success ? 'success' : 'danger'} strong>
              {testResult.success ? '连接成功' : '连接失败'}
            </Typography.Text>
            <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
              {testResult.message}
            </Typography.Paragraph>
            {testResult.product && (
              <Typography.Text type="secondary" className="mono-text">
                {testResult.product} {testResult.version}
              </Typography.Text>
            )}
          </Space>
        )}
      </Modal>
    </div>
  );
}