import type { ReactNode } from 'react';
import { CircleAlert } from 'lucide-react';
import styles from './StateBox.module.css';

interface ErrorStateProps {
  title?: string;
  description?: string;
  action?: ReactNode;
}

/** 错误状态：原因 + 解决路径 + 可操作按钮。 */
export function ErrorState({
  title = '操作失败',
  description,
  action,
}: ErrorStateProps) {
  return (
    <div className={styles.box} role="alert">
      <span className={styles.icon} aria-hidden="true">
        <CircleAlert size={64} strokeWidth={1.5} />
      </span>
      <p className={styles.title}>{title}</p>
      {description && <p className={styles.desc}>{description}</p>}
      {action && <div className={styles.action}>{action}</div>}
    </div>
  );
}