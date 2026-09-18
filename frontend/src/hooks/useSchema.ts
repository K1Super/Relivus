import { useQuery } from '@tanstack/react-query';
import { schemaApi } from '@/api/schema';

export function useSchemaTables(connId: number | undefined) {
  return useQuery({
    queryKey: ['schema', connId, 'tables'],
    queryFn: () => schemaApi.tables(connId as number),
    enabled: connId != null,
  });
}

export function useTableDetail(connId: number | undefined, table: string | undefined) {
  return useQuery({
    queryKey: ['schema', connId, 'table', table],
    queryFn: () => schemaApi.table(connId as number, table as string),
    enabled: connId != null && table != null,
  });
}

export function useDependencyGraph(connId: number | undefined) {
  return useQuery({
    queryKey: ['schema', connId, 'dependencies'],
    queryFn: () => schemaApi.dependencies(connId as number),
    enabled: connId != null,
  });
}