import { Tag } from 'antd';
import type { TaskStatus } from '@/types/api';

const STATUS_META: Record<TaskStatus, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '待执行' },
  RUNNING: { color: 'processing', label: '运行中' },
  SUCCESS: { color: 'success', label: '成功' },
  FAILED: { color: 'error', label: '失败' },
  CANCELLED: { color: 'warning', label: '已取消' },
};

/** 任务状态标签（状态色映射）。 */
export function TaskStatusTag({ status }: { status: TaskStatus }) {
  const meta = STATUS_META[status] ?? { color: 'default', label: status };
  return <Tag color={meta.color}>{meta.label}</Tag>;
}

export const TASK_TYPE_TEXT: Record<string, string> = {
  GENERATION: '数据生成',
  MASKING: '数据脱敏',
};