import { useState } from 'react';
import {
  Alert,
  App,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { Check, KeyRound, Pencil, Plus, Send, Trash2, Zap } from 'lucide-react';
import { AUTH_ERROR_CODE } from '@/api/client';
import { connectionApi } from '@/api/connection';
import { aiApi } from '@/api/ai';
import { useAuthStore } from '@/store/useAuthStore';
import { EmptyState } from '@/components/EmptyState';
import { ErrorState } from '@/components/ErrorState';
import {
  ApiError,
  describeError,
  type AiConfigUpsertRequest,
  type AiModelConfig,
} from '@/types/api';

interface SettingsFormValues {
  token: string;
}

interface AiConfigFormValues {
  name: string;
  baseUrl: string;
  model: string;
  apiKey?: string;
}

const AI_CONFIGS_KEY = ['ai-configs'];

/** AI 模型配置面板：列表、新建/编辑、测试、激活、删除（TanStack Query 管理）。 */
function AiConfigPanel() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<AiConfigFormValues>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AiModelConfig | null>(null);

  const configs = useQuery({
    queryKey: AI_CONFIGS_KEY,
    queryFn: () => aiApi.listAiConfigs(),
  });

  const createMutation = useMutation({
    mutationFn: (req: AiConfigUpsertRequest) => aiApi.createAiConfig(req),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: AI_CONFIGS_KEY }),
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, req }: { id: number; req: AiConfigUpsertRequest }) =>
      aiApi.updateAiConfig(id, req),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: AI_CONFIGS_KEY }),
  });
  const deleteMutation = useMutation({
    mutationFn: (id: number) => aiApi.deleteAiConfig(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: AI_CONFIGS_KEY }),
  });
  const activateMutation = useMutation({
    mutationFn: (id: number) => aiApi.activateAiConfig(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: AI_CONFIGS_KEY }),
  });
  const testMutation = useMutation({
    mutationFn: (id: number) => aiApi.testAiConfig(id),
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (row: AiModelConfig) => {
    setEditing(row);
    form.resetFields();
    form.setFieldsValue({
      name: row.name,
      baseUrl: row.baseUrl,
      model: row.model,
      apiKey: '',
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const payload: AiConfigUpsertRequest = {
      name: values.name,
      baseUrl: values.baseUrl,
      model: values.model,
      apiKey: values.apiKey?.trim() ?? '',
    };
    try {
      if (editing) {
        await updateMutation.mutateAsync({ id: editing.id, req: payload });
        message.success('AI 配置已更新');
      } else {
        await createMutation.mutateAsync(payload);
        message.success('AI 配置已创建');
      }
      setModalOpen(false);
    } catch (err) {
      message.error(describeError(err));
    }
  };

  const runActivate = async (id: number) => {
    try {
      await activateMutation.mutateAsync(id);
      message.success('已设为激活配置');
    } catch (err) {
      message.error(describeError(err));
    }
  };

  const runTest = async (row: AiModelConfig) => {
    try {
      const result = await testMutation.mutateAsync(row.id);
      message.success(result.detail);
    } catch (err) {
      message.error(describeError(err));
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteMutation.mutateAsync(id);
      message.success('配置已删除');
    } catch (err) {
      message.error(describeError(err));
    }
  };

  const columns: ColumnsType<AiModelConfig> = [
    { title: '名称', dataIndex: 'name', render: (name: string) => <Typography.Text strong>{name}</Typography.Text> },
    {
      title: 'API 地址',
      dataIndex: 'baseUrl',
      render: (baseUrl: string) => <span className="mono-text">{baseUrl}</span>,
    },
    { title: '模型', dataIndex: 'model', width: 160, render: (model: string) => <span className="mono-text">{model}</span> },
    {
      title: '激活状态',
      dataIndex: 'active',
      width: 100,
      render: (active: boolean) =>
        active ? <Tag color="green">已激活</Tag> : <Tag>未激活</Tag>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (createdAt: string) => dayjs(createdAt).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      render: (_, row) => (
        <Space size={4}>
          {!row.active && (
            <Button
              size="small"
              icon={<Check size={14} />}
              onClick={() => runActivate(row.id)}
            >
              设为激活
            </Button>
          )}
          <Button
            size="small"
            icon={<Zap size={14} />}
            loading={testMutation.isPending}
            onClick={() => runTest(row)}
          >
            测试
          </Button>
          <Button size="small" icon={<Pencil size={14} />} onClick={() => openEdit(row)}>
            编辑
          </Button>
          <Popconfirm
            title="删除配置"
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
    <div className="section-card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div className="section-title" style={{ margin: 0 }}>
          模型配置
        </div>
        <Button type="primary" icon={<Plus size={16} />} onClick={openCreate}>
          新建配置
        </Button>
      </div>

      {configs.isLoading ? (
        <Table<AiModelConfig> rowKey="id" columns={columns} dataSource={[]} loading pagination={false} />
      ) : configs.isError ? (
        <ErrorState
          title="AI 配置加载失败"
          description={describeError(configs.error)}
          action={<Button onClick={() => configs.refetch()}>重试</Button>}
        />
      ) : (configs.data?.length ?? 0) === 0 ? (
        <EmptyState
          title="还没有 AI 模型配置"
          description="点击右上角按钮添加第一个模型配置"
          action={
            <Button type="primary" icon={<Plus size={16} />} onClick={openCreate}>
              新建配置
            </Button>
          }
        />
      ) : (
        <Table<AiModelConfig>
          rowKey="id"
          columns={columns}
          dataSource={configs.data}
          pagination={{ pageSize: 20, showSizeChanger: true, pageSizeOptions: [20, 50, 100] }}
        />
      )}

      <Modal
        title={editing ? `编辑配置：${editing.name}` : '新建 AI 模型配置'}
        open={modalOpen}
        onOk={submit}
        onCancel={() => setModalOpen(false)}
        okText={editing ? '保存' : '创建'}
        width={560}
        destroyOnHidden
        confirmLoading={createMutation.isPending || updateMutation.isPending}
      >
        <Form form={form} layout="vertical" requiredMark="optional" style={{ marginTop: 8 }}>
          <Form.Item name="name" label="配置名称" rules={[{ required: true, message: '请输入配置名称' }]}>
            <Input placeholder="例如：DeepSeek 生产" maxLength={128} />
          </Form.Item>
          <Form.Item
            name="baseUrl"
            label="API 地址"
            rules={[{ required: true, message: '请输入 API 地址' }]}
            extra="DeepSeek：https://api.deepseek.com/v1；GLM：https://open.bigmodel.cn/api/paas/v4"
          >
            <Input placeholder="https://api.deepseek.com/v1" />
          </Form.Item>
          <Form.Item name="model" label="模型" rules={[{ required: true, message: '请输入模型' }]}>
            <Input placeholder="例如：deepseek-chat" maxLength={128} />
          </Form.Item>
          <Form.Item
            name="apiKey"
            label={editing ? 'API Key（编辑时留空保持不变）' : 'API Key'}
            rules={editing ? [] : [{ required: true, message: '请输入 API Key' }]}
          >
            <Input.Password placeholder={editing ? '留空代表不修改原密钥' : '模型服务商的 API Key'} maxLength={1024} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

/** 设置页：Bearer Token 管理 + AI 模型配置（统一 localStorage key relivusToken）。 */
export default function SettingsPage() {
  const { message } = App.useApp();
  const { token, setToken, clearToken } = useAuthStore();
  const [form] = Form.useForm<SettingsFormValues>();
  const [testing, setTesting] = useState(false);

  const save = async (values: SettingsFormValues) => {
    if (!values.token?.trim()) {
      message.warning('请输入 Token');
      return;
    }
    setToken(values.token);
    message.success('Token 已保存');
  };

  const runAuthTest = async () => {
    if (!token) {
      message.warning('请先保存 Token');
      return;
    }
    setTesting(true);
    try {
      const list = await connectionApi.list();
      message.success(`鉴权成功，可访问 ${list.length} 个连接`);
    } catch (err) {
      if (err instanceof ApiError && err.code === AUTH_ERROR_CODE) {
        message.error('鉴权失败：Token 与后端 RELIVUS_TOKEN 不一致');
      } else {
        message.error(describeError(err));
      }
    } finally {
      setTesting(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">设置</h1>
          <p className="page-subtitle">后端所有 /api 接口均要求 Authorization: Bearer Token（生产环境 RELIVUS_TOKEN）。</p>
        </div>
      </div>

      <Tabs
        defaultActiveKey="token"
        items={[
          {
            key: 'token',
            label: '鉴权 Token',
            children: (
              <div style={{ maxWidth: 640 }}>
                <div className="section-card">
                  <div className="section-title">Bearer Token</div>
                  <Alert
                    type="info"
                    showIcon
                    style={{ marginBottom: 16 }}
                    message="Token 存储位置"
                    description="保存到浏览器 localStorage（key: relivusToken），Axios 与 SSE 均从同一位置读取。生产构建不会使用 VITE_RELIVUS_TOKEN，该变量仅供本地开发预填。"
                  />
                  <Form
                    form={form}
                    layout="vertical"
                    initialValues={{ token }}
                    onFinish={save}
                  >
                    <Form.Item
                      name="token"
                      label="访问令牌"
                      rules={[{ required: true, message: '请输入 Token' }]}
                    >
                      <Input.Password
                        prefix={<KeyRound size={14} />}
                        placeholder="粘贴后端 RELIVUS_TOKEN"
                      />
                    </Form.Item>
                    <div style={{ display: 'flex', gap: 8 }}>
                      <Button type="primary" htmlType="submit" icon={<Send size={14} />}>
                        保存
                      </Button>
                      <Button
                        onClick={runAuthTest}
                        loading={testing}
                        disabled={!token}
                      >
                        验证 Token
                      </Button>
                      {token && (
                        <Button danger type="text" onClick={clearToken}>
                          清除
                        </Button>
                      )}
                    </div>
                  </Form>
                  <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginTop: 16, marginBottom: 0 }}>
                    当前状态：{token ? '已配置' : '未配置'}
                  </Typography.Paragraph>
                </div>
              </div>
            ),
          },
          {
            key: 'ai',
            label: 'AI 模型配置',
            children: <AiConfigPanel />,
          },
        ]}
      />
    </div>
  );
}