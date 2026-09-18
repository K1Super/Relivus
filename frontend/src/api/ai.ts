import { api } from './client';
import type {
  AiConfigTestResult,
  AiConfigUpsertRequest,
  AiModelConfig,
} from '@/types/api';

export const aiApi = {
  listAiConfigs: () => api.get<never, AiModelConfig[]>('/ai/configs'),
  createAiConfig: (req: AiConfigUpsertRequest) =>
    api.post<never, AiModelConfig>('/ai/configs', req),
  updateAiConfig: (id: number, req: AiConfigUpsertRequest) =>
    api.put<never, AiModelConfig>(`/ai/configs/${id}`, req),
  deleteAiConfig: (id: number) => api.delete<never, null>(`/ai/configs/${id}`),
  activateAiConfig: (id: number) =>
    api.post<never, AiModelConfig>(`/ai/configs/${id}/activate`),
  testAiConfig: (id: number) =>
    api.post<never, AiConfigTestResult>(`/ai/configs/${id}/test`),
};