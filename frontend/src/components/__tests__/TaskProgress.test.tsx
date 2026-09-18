import type { FetchEventSourceInit } from '@microsoft/fetch-event-source';
import { act, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TaskProgress } from '../TaskProgress';

type Handler = NonNullable<FetchEventSourceInit['onmessage']>;
const fetchEventSourceMock = vi.fn();
let onmessage: Handler | undefined;

vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: (_url: string, init: FetchEventSourceInit) => {
    onmessage = init.onmessage;
    fetchEventSourceMock(_url);
  },
}));

function dispatch(event: string, data: string) {
  act(() => {
    onmessage?.({ event, data, id: '', retry: 0 } as never);
  });
}

describe('TaskProgress 组件', () => {
  beforeEach(() => {
    fetchEventSourceMock.mockClear();
    onmessage = undefined;
  });

  it('订阅进度与日志事件并渲染', async () => {
    render(<TaskProgress taskId={1} />);
    await waitFor(() => expect(fetchEventSourceMock).toHaveBeenCalledWith('/api/tasks/1/progress'));

    dispatch('progress', JSON.stringify({ taskId: 1, progress: 60, processed: 600, total: 1000 }));
    dispatch('log', JSON.stringify({ level: 'INFO', message: '正在写入批次 3' }));

    expect(screen.getByText('60%')).toBeInTheDocument();
    expect(screen.getByText('已处理 600 / 1,000 行')).toBeInTheDocument();
    expect(screen.getByText('正在写入批次 3')).toBeInTheDocument();
  });

  it('错误事件展示错误详情', async () => {
    render(<TaskProgress taskId={1} />);
    await waitFor(() => expect(fetchEventSourceMock).toHaveBeenCalled());

    dispatch('error', JSON.stringify({ taskId: 1, message: '唯一约束冲突超限' }));

    expect(await screen.findByText('任务执行出错')).toBeInTheDocument();
    expect(screen.getByText('唯一约束冲突超限')).toBeInTheDocument();
  });

  it('showLogs=false 时不渲染日志面板', async () => {
    render(<TaskProgress taskId={1} showLogs={false} />);
    await waitFor(() => expect(fetchEventSourceMock).toHaveBeenCalled());

    dispatch('log', JSON.stringify({ level: 'INFO', message: '隐藏的日志' }));
    expect(screen.queryByText('隐藏的日志')).not.toBeInTheDocument();
  });
});