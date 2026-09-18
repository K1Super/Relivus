import { useEffect, useState } from 'react';
import { App, Button, Drawer, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { RefreshCw, Table2, XCircle } from 'lucide-react';
import dayjs from 'dayjs';
import { useSearchParams } from 'react-router-dom';
import { useCancelTask, useTask, useTasks } from '@/hooks/useTasks';
import { TaskStatusTag, TASK_TYPE_TEXT } from '@/components/TaskStatusTag';
import { TaskProgress } from '@/components/TaskProgress';
import { TaskDataModal } from '@/components/TaskDataModal';
import { ErrorState } from '@/components/ErrorState';
import { describeError, type TaskResponse } from '@/types/api';

function fmtTime(iso?: string | null): string {
  return iso ? dayjs(iso).format('MM-DD HH:mm:ss') : '—';
}

/** 任务监控页：统一任务列表 + SSE 进度/日志 + 取消（DOC-07）。 */
export default function TasksPage() {
  const { message } = App.useApp();
  const [searchParams, setSearchParams] = useSearchParams();
  const focusId = searchParams.get('focus');
  const tasks = useTasks(20, 0);
  const cancelMutation = useCancelTask();

  // 列表初始化时未指定 focus，则不自动打开详情
  const [drawerId, setDrawerId] = useState<number | undefined>(
    focusId ? Number(focusId) : undefined,
  );
  // 生成数据回看弹窗（仅 SUCCESS 生成任务可打开）
  const [dataTaskId, setDataTaskId] = useState<number | undefined>();

  useEffect(() => {
    setSearchParams({}, { replace: true });
  }, [setSearchParams]);

  const detail = useTask(drawerId);

  const handleCancel = async (id: number) => {
    try {
      await cancelMutation.mutateAsync(id);
      message.success('已请求取消任务');
    } catch (err) {
      message.error(describeError(err));
    }
  };

  const columns: ColumnsType<TaskResponse> = [
    { title: 'ID', dataIndex: 'id', width: 64, className: 'num-cell' },
    {
      title: '类型',
      dataIndex: 'taskType',
      width: 120,
      render: (t: TaskResponse['taskType']) => TASK_TYPE_TEXT[t] ?? t,
    },
    { title: '连接', dataIndex: 'connectionId', width: 80, className: 'num-cell' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 96,
      render: (s: TaskResponse['status']) => <TaskStatusTag status={s} />,
    },
    {
      title: '进度',
      key: 'progress',
      render: (_, row) => (
        <Space size={8} style={{ width: '100%' }}>
          <div className="progress-bar" style={{ flex: 1, minWidth: 80 }}>
            <div
              className="progress-bar__fill"
              style={{ transform: `scaleX(${(row.progress ?? 0) / 100})` }}
            />
          </div>
          <span className="mono-text">{row.progress ?? 0}%</span>
        </Space>
      ),
    },
    {
      title: '处理行数',
      key: 'rows',
      width: 140,
      className: 'num-cell',
      render: (_, row) => (
        <span className="mono-text">
          {row.processedRows.toLocaleString()} / {row.totalRows.toLocaleString()}
        </span>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 140,
      render: (v: string) => <span className="mono-text">{fmtTime(v)}</span>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      render: (_, row) => (
        <Space size={4}>
          <Button size="small" onClick={() => setDrawerId(row.id)}>
            查看
          </Button>
          <Button
            size="small"
            icon={<Table2 size={14} />}
            disabled={!(row.status === 'SUCCESS' && row.taskType === 'GENERATION')}
            title={row.taskType === 'GENERATION' && row.status === 'SUCCESS' ? '可视化本次生成的数据' : '仅成功完成的生成任务可回看数据'}
            onClick={() => setDataTaskId(row.id)}
          >
            查看数据
          </Button>
          <Button
            size="small"
            danger
            icon={<XCircle size={14} />}
            disabled={!['PENDING', 'RUNNING'].includes(row.status)}
            onClick={() => handleCancel(row.id)}
          >
            取消
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">任务监控</h1>
          <p className="page-subtitle">统一任务接口 /api/tasks；运行中任务自动轮询刷新，点击查看订阅 SSE 实时进度。</p>
        </div>
        <Button icon={<RefreshCw size={16} />} onClick={() => tasks.refetch()}>
          刷新
        </Button>
      </div>

      <div className="section-card">
        {tasks.isLoading ? (
          <Table<TaskResponse> rowKey="id" columns={columns} dataSource={[]} loading pagination={false} />
        ) : tasks.isError ? (
          <ErrorState title="任务列表加载失败" description={describeError(tasks.error)} action={<Button onClick={() => tasks.refetch()}>重试</Button>} />
        ) : (tasks.data?.length ?? 0) === 0 ? (
          <Typography.Text type="secondary" style={{ display: 'block', padding: '48px 0', textAlign: 'center' }}>
            暂无任务，前往「数据生成」或「数据脱敏」发起第一个任务。
          </Typography.Text>
        ) : (
          <Table<TaskResponse>
            rowKey="id"
            size="small"
            columns={columns}
            dataSource={tasks.data}
            pagination={{ pageSize: 20, showSizeChanger: true, pageSizeOptions: [20, 50, 100] }}
          />
        )}
      </div>

      <Drawer
        title={`任务 #${drawerId ?? ''} 详情`}
        open={drawerId != null}
        onClose={() => setDrawerId(undefined)}
        width={560}
        destroyOnHidden
      >
        {detail.isLoading ? (
          <Typography.Text type="secondary">加载任务信息…</Typography.Text>
        ) : detail.isError ? (
          <ErrorState title="任务详情加载失败" description={describeError(detail.error)} />
        ) : detail.data ? (
          <>
            <Space direction="vertical" size={8} style={{ width: '100%', marginBottom: 16 }}>
              <Space>
                <TaskStatusTag status={detail.data.status} />
                <Typography.Text type="secondary">
                  {TASK_TYPE_TEXT[detail.data.taskType]} · 连接 #{detail.data.connectionId}
                </Typography.Text>
              </Space>
              <Typography.Text type="secondary" className="mono-text" style={{ fontSize: 12 }}>
                创建 {fmtTime(detail.data.createdAt)} · 开始 {fmtTime(detail.data.startedAt)} · 结束 {fmtTime(detail.data.finishedAt)}
              </Typography.Text>
              {detail.data.errorMessage && (
                <Typography.Text type="danger">{detail.data.errorMessage}</Typography.Text>
              )}
            </Space>
            <TaskProgress taskId={drawerId as number} />
            <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
              {detail.data.status === 'SUCCESS' && detail.data.taskType === 'GENERATION' && (
                <Button
                  type="primary"
                  icon={<Table2 size={14} />}
                  onClick={() => setDataTaskId(drawerId as number)}
                >
                  查看生成数据
                </Button>
              )}
              <Button
                danger
                icon={<XCircle size={14} />}
                disabled={!['PENDING', 'RUNNING'].includes(detail.data.status)}
                onClick={() => handleCancel(drawerId as number)}
              >
                取消任务
              </Button>
            </div>
          </>
        ) : null}
      </Drawer>

      <TaskDataModal
        taskId={dataTaskId}
        open={dataTaskId != null}
        onClose={() => setDataTaskId(undefined)}
      />
    </div>
  );
}