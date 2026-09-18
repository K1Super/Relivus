import { api, newIdempotencyKey } from './client';
import type { GenerationConfig, GenerationRunResult, TaskResponse } from '@/types/api';

export const generationApi = {
  preview: (config: GenerationConfig) =>
    api.post<never, GenerationRunResult>('/generation/preview', config, {
      headers: { 'Idempotency-Key': newIdempotencyKey() },
    }),
  execute: (config: GenerationConfig) =>
    api.post<never, TaskResponse>('/generation/execute', config, {
      headers: { 'Idempotency-Key': newIdempotencyKey() },
    }),
};