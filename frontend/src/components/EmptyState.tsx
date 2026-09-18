import type { ReactNode } from 'react';
import { Inbox } from 'lucide-react';
import styles from './StateBox.module.css';

interface EmptyStateProps {
  title?: string;
  description?: string;
  action?: ReactNode;
}

/** 空状态：图标 + 文案 + 操作（UI 规范 5.2）。 */
export function EmptyState({
  title = '暂无数据',
  description,
  action,
}: EmptyStateProps) {
  return (
    <div className={styles.box}>
      <span className={styles.icon} aria-hidden="true">
        <Inbox size={64} strokeWidth={1.5} />
      </span>
      <p className={styles.title}>{title}</p>
      {description && <p className={styles.desc}>{description}</p>}
      {action && <div className={styles.action}>{action}</div>}
    </div>
  );
}