import { Suspense, lazy, useEffect, useMemo } from 'react';
import { Spin } from 'antd';
import { App as AntApp } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  BrowserRouter,
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router-dom';
import { AppLayout } from '@/components/AppLayout';
import { onAuthFailure } from '@/api/client';

const ConnectionsPage = lazy(() => import('@/pages/ConnectionsPage'));
const SchemaPage = lazy(() => import('@/pages/SchemaPage'));
const GenerationPage = lazy(() => import('@/pages/GenerationPage'));
const MaskingPage = lazy(() => import('@/pages/MaskingPage'));
const TasksPage = lazy(() => import('@/pages/TasksPage'));
const SettingsPage = lazy(() => import('@/pages/SettingsPage'));

/** 鉴权失败（100003 / HTTP 401）统一跳转设置页（DOC-07 Token 管理）。 */
function AuthRedirect() {
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    onAuthFailure(() => {
      if (location.pathname !== '/settings') {
        navigate('/settings', { state: { from: location.pathname } });
      }
    });
  }, [navigate, location.pathname]);

  return null;
}

const LoadingFallback = (
  <div style={{ display: 'flex', justifyContent: 'center', padding: '64px 0' }}>
    <Spin size="large" aria-label="页面加载中" />
  </div>
);

/** 应用根：QueryClient + antd App 上下文 + 路由（页面级懒加载）。 */
export default function App() {
  const queryClient = useMemo(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: 1,
            refetchOnWindowFocus: false,
            staleTime: 30_000,
          },
        },
      }),
    [],
  );

  return (
    <QueryClientProvider client={queryClient}>
      <AntApp>
        <BrowserRouter
          future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
        >
          <AuthRedirect />
          <Routes>
            <Route
              path="/"
              element={
                <AppLayout>
                  <Suspense fallback={LoadingFallback}>
                    <Outlet />
                  </Suspense>
                </AppLayout>
              }
            >
              <Route index element={<Navigate to="/connections" replace />} />
              <Route path="connections" element={<ConnectionsPage />} />
              <Route path="schema" element={<SchemaPage />} />
              <Route path="schema/:connId" element={<SchemaPage />} />
              <Route path="generation" element={<GenerationPage />} />
              <Route path="masking" element={<MaskingPage />} />
              <Route path="tasks" element={<TasksPage />} />
              <Route path="settings" element={<SettingsPage />} />
              <Route path="*" element={<Navigate to="/connections" replace />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AntApp>
    </QueryClientProvider>
  );
}