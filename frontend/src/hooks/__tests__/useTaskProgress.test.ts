import type { FetchEventSourceInit } from '@microsoft/fetch-event-source';
import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useTaskProgress } from '@/hooks/useTaskProgress';

type Handler = NonNullable<FetchEventSourceInit['onmessage']>;

const fetchEventSourceMock = vi.fn();
let capturedOnmessage: Handler | undefined;
let capturedHeaders: Record<string, string> | undefined;

vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: (url: string, init: FetchEventSourceInit) => {
    capturedHeaders = init.headers as Record<string, string>;
    capturedOnmessage = init.onmessage;
    fetchEventSourceMock(url);
  },
}));

function dispatch(event: string, data: string) {
  act(() => {
    capturedOnmessage?.({ event, data, id: '', retry: 0 } as never);
  });
}

describe('useTaskProgress（SSE）', () => {
  beforeEach(() => {
    localStorage.clear();
    capturedOnmessage = undefined;
    capturedHeaders = undefined;
    fetchEventSourceMock.mockClear();
  });

  it('携带 Authorization 并消费 progress 事件', async () => {
    localStorage.setItem('relivusToken', 'sse-token');
    const { result } = renderHook(() => useTaskProgress(1));

    await waitFor(() =>
      expect(fetchEventSourceMock).toHaveBeenCalledWith('/api/tasks/1/progress'),
    );
    expect(capturedHeaders?.Authorization).toBe('Bearer sse-token');

    dispatch('progress', JSON.stringify({ taskId: 1, progress: 50, processed: 500, total: 1000 }));
    expect(result.current.progress).toBe(50);
    expect(result.current.processed).toBe(500);
    expect(result.current.total).toBe(1000);
  });

  it('消费 log 事件并追加日志', () => {
    const { result } = renderHook(() => useTaskProgress(1));
    dispatch('log', JSON.stringify({ level: 'INFO', message: '开始生成' }));
    dispatch('log', JSON.stringify({ level: 'WARN', message: '重试外键采样' }));
    expect(result.current.logs).toHaveLength(2);
    expect(result.current.logs[0].message).toBe('开始生成');
    expect(result.current.logs[1].message).toBe('重试外键采样');
  });

  it('done 事件后进入 finished 状态', () => {
    const { result } = renderHook(() => useTaskProgress(1));
    dispatch('done', JSON.stringify({ taskId: 1, status: 'SUCCESS' }));
    expect(result.current.status).toBe('done');
    expect(result.current.finished).toBe(true);
    expect(result.current.progress).toBe(100);
  });

  it('error 事件记录错误信息', () => {
    const { result } = renderHook(() => useTaskProgress(1));
    dispatch('error', JSON.stringify({ taskId: 1, message: '生成失败' }));
    expect(result.current.status).toBe('error');
    expect(result.current.errorMessage).toBe('生成失败');
  });
});