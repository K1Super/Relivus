import { api } from './client';
import type { TaskDataPage, TaskGeneratedTable, TaskResponse } from '@/types/api';

export const taskApi = {
  list: (limit = 20, offset = 0) =>
    api.get<never, TaskResponse[]>('/tasks', { params: { limit, offset } }),
  get: (id: number) => api.get<never, TaskResponse>(`/tasks/${id}`),
  cancel: (id: number) => api.post<never, boolean>(`/tasks/${id}/cancel`),
  /** 生成任务数据回看：表清单（DOC-06）。 */
  tables: (id: number) => api.get<never, TaskGeneratedTable[]>(`/tasks/${id}/tables`),
  /** 生成任务数据回看：分页数据（DOC-06）。 */
  data: (id: number, table: string, limit = 100, offset = 0) =>
    api.get<never, TaskDataPage>(`/tasks/${id}/data`, { params: { table, limit, offset } }),
  /** 进度 SSE 端点（由 fetch-event-source 消费；这里仅导出路径拼装）。 */
  progressUrl: (id: number) => `/api/tasks/${id}/progress`,
};