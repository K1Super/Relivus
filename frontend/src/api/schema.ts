import { api } from './client';
import type { DependencyGraph, SchemaTableSummary, TableDetail } from '@/types/api';

export const schemaApi = {
  tables: (connId: number) =>
    api.get<never, SchemaTableSummary[]>(`/schema/${connId}/tables`),
  table: (connId: number, table: string) =>
    api.get<never, TableDetail>(`/schema/${connId}/tables/${encodeURIComponent(table)}`),
  dependencies: (connId: number) =>
    api.get<never, DependencyGraph>(`/schema/${connId}/dependencies`),
};