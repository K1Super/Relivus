import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ProgressBar } from '../ProgressBar';

describe('ProgressBar', () => {
  it('渲染百分比与正确的 scaleX 缩放', () => {
    render(<ProgressBar percent={45} />);
    const bar = screen.getByRole('progressbar');
    expect(bar).toHaveAttribute('aria-valuenow', '45');
    expect(bar).toHaveAttribute('aria-valuemin', '0');
    expect(bar).toHaveAttribute('aria-valuemax', '100');
    const fill = bar.firstElementChild as HTMLElement;
    expect(fill.style.transform).toBe('scaleX(0.45)');
    expect(screen.getByText('45%')).toBeInTheDocument();
  });

  it('越界百分比被收敛到 0-100', () => {
    render(<ProgressBar percent={150} />);
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100');
  });

  it('支持自定义 aria-label', () => {
    render(<ProgressBar percent={0} ariaLabel="生成进度" />);
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-label', '生成进度');
  });
});