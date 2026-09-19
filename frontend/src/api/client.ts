import axios, { AxiosError } from 'axios';
import { ApiError, type ApiResponse } from '@/types/api';

/** Token 存 localStorage 的统一 key（Axios 与 SSE 共用）。 */
export const API_TOKEN_KEY = 'relivusToken';

/** 成功码（后端 ApiResponse.code=0 表示成功）。 */
export const SUCCESS_CODE = 0;

/** 鉴权失败错误码（后端 TokenAuthFilter 返回 100003）。 */
export const AUTH_ERROR_CODE = 100003;

/** 网络层错误不经业务码包裹，按本地码 999000 兜底。 */
const HTTP_ERROR_CODE = 999000;

/**
 * Axios 实例：
 * - 请求拦截器注入 `Authorization: Bearer ${token}`。
 * - 响应拦截器解包 ApiResponse<T>：code=0 返回 data；code!=0 抛 ApiError。
 * - 鉴权失败（HTTP 401 或码 100003）通知订阅方跳转设置页。
 */
export const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

/**
 * 当前生效 Token：优先 localStorage（生产/设置页写入），
 * dev 模式回退 VITE_RELIVUS_TOKEN（仅供本地开发，不用于生产构建）。
 */
export function resolveToken(): string {
  const stored = localStorage.getItem(API_TOKEN_KEY);
  if (stored) {
    return stored;
  }
  if (import.meta.env.DEV && import.meta.env.VITE_RELIVUS_TOKEN) {
    return import.meta.env.VITE_RELIVUS_TOKEN;
  }
  return '';
}

api.interceptors.request.use(config => {
  const token = resolveToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

type AuthListener = () => void;
let authListener: AuthListener | null = null;

/** 注册鉴权失败回调（App 根用于跳转设置页）；重复注册覆盖。 */
export function onAuthFailure(listener: AuthListener): void {
  authListener = listener;
}

api.interceptors.response.use(
  response => {
    const body = response.data as ApiResponse<unknown>;
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === SUCCESS_CODE) {
        return body.data as never;
      }
      if (body.code === AUTH_ERROR_CODE) {
        authListener?.();
      }
      throw new ApiError(body.code, body.message);
    }
    return body as never;
  },
  (error: AxiosError<{ code?: number; message?: string }>) => {
    if (error.response?.status === 401) {
      authListener?.();
      throw new ApiError(AUTH_ERROR_CODE, '鉴权失败，请到设置页填写 Token');
    }
    const body = error.response?.data;
    if (body && typeof body.code === 'number' && body.code !== SUCCESS_CODE) {
      if (body.code === AUTH_ERROR_CODE) {
        authListener?.();
      }
      throw new ApiError(body.code, body.message ?? error.message);
    }
    throw new ApiError(HTTP_ERROR_CODE, `网络异常：${error.message}`);
  },
);

/** 生成幂等键（execute/preview/verify 请求头 Idempotency-Key）。 */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}