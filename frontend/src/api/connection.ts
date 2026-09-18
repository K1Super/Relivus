import { api } from './client';
import type {
  ConnectionResponse,
  ConnectionTestResult,
  CreateConnectionRequest,
} from '@/types/api';

export const connectionApi = {
  list: () => api.get<never, ConnectionResponse[]>('/connections'),
  create: (req: CreateConnectionRequest) =>
    api.post<never, ConnectionResponse>('/connections', req),
  update: (id: number, req: CreateConnectionRequest) =>
    api.put<never, ConnectionResponse>(`/connections/${id}`, req),
  remove: (id: number) => api.delete<never, void>(`/connections/${id}`),
  test: (id: number) => api.post<never, ConnectionTestResult>(`/connections/${id}/test`),
};