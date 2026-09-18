import { useEffect, useMemo, useState } from 'react';
import { Alert, Modal, Select, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import { Table2 } from 'lucide-react';
import { taskApi } from '@/api/task';
import { ErrorState } from '@/components/ErrorState';
import { describeError, type TaskDataPage } from '@/types/api';

/** 单页行数（与后端 /api/tasks/{id}/data 默认 limit 一致）。 */
const PAGE_SIZE = 100;

/** 单元格渲染：对象（数组/JSON）序列化为紧凑文本，null 显示占位。 */
function formatCell(value: unknown): string {
  if (value === null || value === undefined) {
    return '—';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

/**
 * 生成数据回看弹窗（DOC-06）：任务成功后按表切换 + 服务端分页，
 * 直接可视化本次生成的行数据，无需再打开数据库客户端。
 */
export function TaskDataModal({
  taskId,
  open,
  onClose,
}: {
  taskId: number | undefined;
  open: boolean;
  onClose: () => void;
}) {
  const [table, setTable] = useState<string>();
  const [page, setPage] = useState(1);

  const tablesQuery = useQuery({
    queryKey: ['task-tables', taskId],
    queryFn: () => taskApi.tables(taskId as number),
    enabled: open && taskId != null,
  });

  // 表清单就绪后默认选中第一张表
  useEffect(() => {
    if (!open) {
      return;
    }
    if (table == null && tablesQuery.data?.length) {
      setTable(tablesQuery.data[0].table);
    }
    setPage(1);
  }, [open, table, tablesQuery.data]);

  const dataQuery = useQuery({
    queryKey: ['task-data', taskId, table, page],
    queryFn: () => taskApi.data(taskId as number, table as string, PAGE_SIZE, (page - 1) * PAGE_SIZE),
    enabled: open && taskId != null && table != null,
  });

  const data: TaskDataPage | undefined = dataQuery.data;
  const currentTable = tablesQuery.data?.find(t => t.table === table);

  const columns: ColumnsType<{ key: number; cells: unknown[] }> = useMemo(
    () =>
      (data?.columns ?? []).map((col, idx) => ({
        title: (
          <span>
            {col.name}
            <Typography.Text type="secondary" style={{ fontSize: 11, marginLeft: 6 }}>
              {col.type}
            </Typography.Text>
          </span>
        ),
        key: col.name,
        dataIndex: ['cells', idx],
        ellipsis: true,
        render: (value: unknown) => <span className="mono-text">{formatCell(value)}</span>,
      })),
    [data],
  );

  const rows = useMemo(
    () => (data?.rows ?? []).map((row, i) => ({ key: (page - 1) * PAGE_SIZE + i, cells: row })),
    [data, page],
  );

  return (
    <Modal
      title={
        <Space>
          <Table2 size={16} />
          生成数据回看
          {taskId != null && <Typography.Text type="secondary">任务 #{taskId}</Typography.Text>}
        </Space>
      }
      open={open}
      onCancel={onClose}
      width={1080}
      footer={null}
      destroyOnHidden
    >
      {tablesQuery.isError ? (
        <ErrorState title="表清单加载失败" description={describeError(tablesQuery.error)} />
      ) : (
        <>
          <Space style={{ marginBottom: 12 }} wrap>
            <Select
              aria-label="切换表"
              style={{ width: 260 }}
              placeholder="选择表"
              loading={tablesQuery.isLoading}
              value={table}
              onChange={v => {
                setTable(v);
                setPage(1);
              }}
              options={(tablesQuery.data ?? []).map(t => ({
                value: t.table,
                label: `${t.table}（配置 ${t.rowCount.toLocaleString()} 行）`,
              }))}
            />
            {currentTable && !currentTable.watermarkApplied && (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                该表无数值主键，按全表展示
              </Typography.Text>
            )}
          </Space>

          {dataQuery.isError ? (
            <ErrorState title="数据加载失败" description={describeError(dataQuery.error)} />
          ) : (
            <Table<{ key: number; cells: unknown[] }>
              rowKey="key"
              size="small"
              columns={columns}
              dataSource={rows}
              loading={dataQuery.isLoading || dataQuery.isFetching}
              scroll={{ x: 'max-content', y: 480 }}
              pagination={{
                current: page,
                pageSize: PAGE_SIZE,
                total: data?.total ?? 0,
                showSizeChanger: false,
                showTotal: total => `共 ${total.toLocaleString()} 行`,
                onChange: setPage,
              }}
              locale={{ emptyText: data?.total === 0 ? '本次未生成该表数据' : '暂无数据' }}
            />
          )}

          {currentTable && !currentTable.watermarkApplied && (
            <Alert
              type="info"
              showIcon
              style={{ marginTop: 12 }}
              message="表未设置数值主键基线，当前展示该表全量数据，可能包含任务前已存在的行。"
            />
          )}
        </>
      )}
    </Modal>
  );
}
