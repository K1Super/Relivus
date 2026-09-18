import { Alert, Typography } from 'antd';
import { useTaskProgress } from '@/hooks/useTaskProgress';
import { ProgressBar } from './ProgressBar';
import { LogPanel } from './LogPanel';

const STATUS_TEXT: Record<string, string> = {
  connecting: '连接中…',
  live: '实时更新中',
  done: '已结束',
  error: '已终止',
  closed: '已关闭',
};

interface TaskProgressProps {
  taskId: number;
  showLogs?: boolean;
}

/** 任务进度面板：进度条 + 行数统计 + 结构化日志（订阅 SSE，任务中心复用）。 */
export function TaskProgress({ taskId, showLogs = true }: TaskProgressProps) {
  const { progress, processed, total, status, errorMessage, logs } = useTaskProgress(taskId);

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'baseline',
          marginBottom: 8,
        }}
      >
        <Typography.Text type="secondary" style={{ fontSize: 13 }}>
          {STATUS_TEXT[status] ?? status}
        </Typography.Text>
        <Typography.Text className="num-cell" type="secondary" style={{ fontSize: 12 }}>
          已处理 {processed.toLocaleString()} / {total.toLocaleString()} 行
        </Typography.Text>
      </div>
      <ProgressBar percent={progress} ariaLabel="任务进度" />
      {status === 'error' && errorMessage && (
        <Alert
          style={{ marginTop: 16 }}
          type="error"
          showIcon
          message="任务执行出错"
          description={errorMessage}
        />
      )}
      {showLogs && (
        <div style={{ marginTop: 16 }}>
          <LogPanel logs={logs} />
        </div>
      )}
    </div>
  );
}