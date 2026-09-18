import type { TaskLogEntry } from '@/hooks/useTaskProgress';
import styles from './LogPanel.module.css';

interface LogPanelProps {
  logs: TaskLogEntry[];
  ariaLabel?: string;
}

const levelClass = (level: TaskLogEntry['level']) => {
  switch (level) {
    case 'INFO':
      return styles.levelInfo;
    case 'WARN':
      return styles.levelWarn;
    default:
      return styles.levelError;
  }
};

/** 动态日志：aria-live=polite 追加式输出（无障碍 8.6.2）。 */
export function LogPanel({ logs, ariaLabel = '任务日志' }: LogPanelProps) {
  return (
    <div
      className={styles.panel}
      role="log"
      aria-label={ariaLabel}
      aria-live="polite"
      aria-atomic="false"
      aria-relevant="additions"
    >
      {logs.length === 0 ? (
        <div className={styles.empty}>暂无日志输出</div>
      ) : (
        logs.map(log => (
          <div key={log.id} className={styles.line}>
            <span className={styles.time}>{log.time}</span>
            <span className={`${styles.level} ${levelClass(log.level)}`}>
              {log.level.padEnd(5, ' ')}
            </span>
            <span className={styles.msg}>{log.message}</span>
          </div>
        ))
      )}
    </div>
  );
}