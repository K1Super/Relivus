import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

/**
 * Vite 构建与 Vitest 单测配置（DOC-07 / UI 规范 13.1、9.6）。
 * - /api 代理到后端 8080，规避浏览器 CORS。
 * - 路由级懒加载 + manualChunks：首屏不含 antd，重依赖各自独立 chunk。
 * - Vitest 走 jsdom，setup 提供 ResizeObserver/matchMedia 等浏览器补丁。
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  preview: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    target: 'es2020',
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          antd: ['antd'],
          query: ['@tanstack/react-query'],
          flow: ['reactflow'],
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});