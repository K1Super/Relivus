import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { taskApi } from '@/api/task';

const TASKS_KEY = ['tasks'];

export function useTasks(limit = 20, offset = 0) {
  return useQuery({
    queryKey: [...TASKS_KEY, limit, offset],
    queryFn: () => taskApi.list(limit, offset),
    // 存在运行中任务时轮询刷新（数据加载目标 ≤3s）
    refetchInterval: query => {
      const tasks = query.state.data;
      return tasks?.some(t => t.status === 'RUNNING' || t.status === 'PENDING') ? 3000 : false;
    },
  });
}

export function useTask(id: number | undefined) {
  return useQuery({
    queryKey: [...TASKS_KEY, id],
    queryFn: () => taskApi.get(id as number),
    enabled: id != null,
    refetchInterval: query => {
      const task = query.state.data;
      return task && (task.status === 'RUNNING' || task.status === 'PENDING') ? 3000 : false;
    },
  });
}

export function useCancelTask() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => taskApi.cancel(id),
    onSuccess: () => client.invalidateQueries({ queryKey: TASKS_KEY }),
  });
}