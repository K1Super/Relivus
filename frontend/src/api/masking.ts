import { api, newIdempotencyKey } from './client';
import type {
  JoinVerificationRequest,
  JoinVerificationResult,
  MaskingPreviewResponse,
  MaskingTaskRequest,
  TaskResponse,
} from '@/types/api';

export const maskingApi = {
  preview: (request: MaskingTaskRequest) =>
    api.post<never, MaskingPreviewResponse>('/masking/preview', request, {
      headers: { 'Idempotency-Key': newIdempotencyKey() },
    }),
  execute: (request: MaskingTaskRequest) =>
    api.post<never, TaskResponse>('/masking/execute', request, {
      headers: { 'Idempotency-Key': newIdempotencyKey() },
    }),
  verify: (request: JoinVerificationRequest) =>
    api.post<never, JoinVerificationResult>('/masking/verify', request, {
      headers: { 'Idempotency-Key': newIdempotencyKey() },
    }),
};