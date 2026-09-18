import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { connectionApi } from '@/api/connection';
import type { CreateConnectionRequest } from '@/types/api';

const CONNECTIONS_KEY = ['connections'];

export function useConnections() {
  return useQuery({
    queryKey: CONNECTIONS_KEY,
    queryFn: () => connectionApi.list(),
  });
}

export function useCreateConnection() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateConnectionRequest) => connectionApi.create(req),
    onSuccess: () => client.invalidateQueries({ queryKey: CONNECTIONS_KEY }),
  });
}

export function useUpdateConnection() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: CreateConnectionRequest }) =>
      connectionApi.update(id, req),
    onSuccess: () => client.invalidateQueries({ queryKey: CONNECTIONS_KEY }),
  });
}

export function useDeleteConnection() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => connectionApi.remove(id),
    onSuccess: () => client.invalidateQueries({ queryKey: CONNECTIONS_KEY }),
  });
}

export function useTestConnection() {
  return useMutation({
    mutationFn: (id: number) => connectionApi.test(id),
  });
}