import { useEffect, useRef, useState } from 'react';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { API_TOKEN_KEY } from '@/api/client';
import type {
  SseDoneEvent,
  SseErrorEvent,
  SseLogEvent,
  SseProgressEvent,
} from '@/types/api';

export interface TaskLogEntry {
  id: number;
  time: string;
  level: 'INFO' | 'WARN' | 'ERROR';
  message: string;
}

export interface TaskProgressState {
  progress: number;
  processed: number;
  total: number;
  status: 'connecting' | 'live' | 'done' | 'error' | 'closed';
  errorMessage?: string;
  finished?: boolean;
}

const initialState: TaskProgressState = {
  progress: 0,
  processed: 0,
  total: 0,
  status: 'connecting',
};

/**
 * 订阅任务进度 SSE。
 * - 使用 @microsoft/fetch-event-source（原生 EventSource 无法携带 Authorization）。
 * - progress / log / done / error 事件解析；done/error 后主动断开。
 * - onerror 默认行为即断线自动重连。
 */
export function useTaskProgress(taskId: number | undefined) {
  const [state, setState] = useState<TaskProgressState>(initialState);
  const [logs, setLogs] = useState<TaskLogEntry[]>([]);
  const idRef = useRef(0);

  const connected = taskId != null && !state.finished;

  useEffect(() => {
    if (taskId == null) {
      setState(initialState);
      setLogs([]);
      return;
    }

    setState(initialState);
    setLogs([]);

    const ctrl = new AbortController();
    let disposed = false;

    const pushLog = (level: TaskLogEntry['level'], message: string) => {
      const now = new Date();
      const pad = (n: number) => String(n).padStart(2, '0');
      const time = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
      setLogs(prev => [...prev.slice(-199), { id: ++idRef.current, time, level, message }]);
    };

    fetchEventSource(`/api/tasks/${taskId}/progress`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${localStorage.getItem(API_TOKEN_KEY) ?? ''}`,
        Accept: 'text/event-stream',
      },
      signal: ctrl.signal,
      openWhenHidden: true,
      onopen: async response => {
        if (!response.ok) {
          throw new Error(`SSE 连接失败：HTTP ${response.status}`);
        }
        if (!disposed) {
          setState(s => ({ ...s, status: 'live' }));
        }
      },
      onmessage(ev) {
        if (disposed || ctrl.signal.aborted) {
          return;
        }
        try {
          if (ev.event === 'progress') {
            const data = JSON.parse(ev.data) as SseProgressEvent;
            setState(s => ({
              ...s,
              progress: data.progress,
              processed: data.processed,
              total: data.total,
            }));
          } else if (ev.event === 'log') {
            const data = JSON.parse(ev.data) as SseLogEvent;
            pushLog(data.level ?? 'INFO', data.message);
          } else if (ev.event === 'done') {
            JSON.parse(ev.data) as SseDoneEvent;
            setState(s => ({ ...s, status: 'done', finished: true, progress: 100 }));
            ctrl.abort();
          } else if (ev.event === 'error') {
            const data = JSON.parse(ev.data) as SseErrorEvent;
            setState(s => ({ ...s, status: 'error', finished: true, errorMessage: data.message }));
            ctrl.abort();
          }
        } catch {
          pushLog('ERROR', 'SSE 数据解析失败');
        }
      },
      onerror(err) {
        // 不抛错 → fetch-event-source 按默认策略断线重连。
        if (!disposed) {
          pushLog('ERROR', `SSE 连接中断，自动重连中：${String(err)}`);
        }
      },
    });

    return () => {
      disposed = true;
      ctrl.abort();
    };
  }, [taskId]);

  return { ...state, logs, connected };
}