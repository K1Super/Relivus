import type { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import {
  Database,
  GitFork,
  ListChecks,
  Network,
  Settings,
  ShieldCheck,
  Wand2,
} from 'lucide-react';
import styles from './AppLayout.module.css';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { to: '/connections', label: '连接管理', icon: Database },
  { to: '/schema', label: 'Schema 浏览', icon: Network },
  { to: '/generation', label: '数据生成', icon: Wand2 },
  { to: '/masking', label: '数据脱敏', icon: ShieldCheck },
  { to: '/tasks', label: '任务监控', icon: ListChecks },
  { to: '/settings', label: '设置', icon: Settings },
];

interface AppLayoutProps {
  children: ReactNode;
  /** 可选：当前版本/状态信息展示于底栏。 */
  connectionLabel?: string;
}

/** 应用外壳：56px 顶栏 + 240px 侧栏 + 32px 底栏。 */
export function AppLayout({ children }: AppLayoutProps) {
  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <div className={styles.logo}>
          <GitFork size={20} strokeWidth={2} aria-hidden="true" />
          <span>Relivus 数据平台</span>
        </div>
        <div className={styles.headerStatus}>
          <span>Bearer Token 鉴权</span>
        </div>
      </header>
      <div className={styles.body}>
        <nav className={styles.sidebar} aria-label="主导航">
          {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                isActive ? `${styles.link} ${styles.linkActive}` : styles.link
              }
            >
              <Icon size={16} strokeWidth={2} aria-hidden="true" />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <main className={styles.content}>{children}</main>
      </div>
      <footer className={styles.footer}>
        <span>v0.1.0</span>
        <span>数据生成与脱敏平台</span>
      </footer>
    </div>
  );
}