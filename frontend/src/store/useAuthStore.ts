import { create } from 'zustand';
import { API_TOKEN_KEY, resolveToken } from '@/api/client';

interface AuthState {
  token: string;
  /** 设置新 Token 并持久化到 localStorage（统一 key relivusToken）。 */
  setToken: (token: string) => void;
  clearToken: () => void;
}

/** Token 状态（DOC-07：生产由设置页输入存 localStorage；dev 回退 VITE_RELIVUS_TOKEN）。 */
export const useAuthStore = create<AuthState>(set => ({
  token: resolveToken(),
  setToken: token => {
    const trimmed = token.trim();
    if (trimmed) {
      localStorage.setItem(API_TOKEN_KEY, trimmed);
    } else {
      localStorage.removeItem(API_TOKEN_KEY);
    }
    set({ token: trimmed });
  },
  clearToken: () => {
    localStorage.removeItem(API_TOKEN_KEY);
    set({ token: '' });
  },
}));

/** 是否已配置 Token。 */
export const useHasToken = () => useAuthStore(s => s.token.length > 0);