import styles from './ProgressBar.module.css';

interface ProgressBarProps {
  percent: number;
  ariaLabel?: string;
}

/** 进度条：只通过 transform: scaleX 驱动（UI 规范 4.7）。 */
export function ProgressBar({ percent, ariaLabel = '任务进度' }: ProgressBarProps) {
  const clamped = Math.max(0, Math.min(100, Math.round(percent)));
  return (
    <div className={styles.wrapper}>
      <div
        className={styles.bar}
        role="progressbar"
        aria-valuenow={clamped}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={ariaLabel}
      >
        <div className={styles.fill} style={{ transform: `scaleX(${clamped / 100})` }} />
      </div>
      <span className={styles.percent}>{clamped}%</span>
    </div>
  );
}