import { beforeEach, describe, expect, it } from 'vitest';
import { api } from '@/api/client';

/** 用自定义 adapter 捕获请求配置并注入假响应，验证拦截器行为。 */
function withAdapter(
  handler: (config: {
    headers: Record<string, string>;
    url: string;
  }) => { code: number; message: string; data: unknown },
) {
  api.defaults.adapter = async config => {
    const body = handler({
      headers: config.headers as Record<string, string>,
      url: config.url ?? '',
    });
    return {
      data: body,
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    };
  };
}

describe('api client 拦截器', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('请求注入 Authorization: Bearer ${localStorageToken}', async () => {
    localStorage.setItem('relivusToken', 'secret-token');
    let captured:'unset' | string = 'unset';
    withAdapter(config => {
      captured = config.headers.Authorization;
      return { code: 0, message: 'success', data: [] };
    });
    await api.get('/connections');
    expect(captured).toBe('Bearer secret-token');
  });

  it('code=0 时解包返回 data', async () => {
    withAdapter(() => ({ code: 0, message: 'success', data: { id: 7 } }));
    const data = await api.get<never, { id: number }>('/x');
    expect(data).toEqual({ id: 7 });
  });

  it('code!=0 时抛出 ApiError 并携带错误码', async () => {
    withAdapter(() => ({ code: 530001, message: '生成失败', data: null }));
    await expect(api.get('/x')).rejects.toMatchObject({
      name: 'ApiError',
      code: 530001,
      message: '生成失败',
    });
  });

  it('未配置 Token 时不注入 Authorization 头', async () => {
    let captured:string | undefined;
    withAdapter(config => {
      captured = config.headers.Authorization;
      return { code: 0, message: 'success', data: null };
    });
    await api.get('/x');
    expect(captured).toBeUndefined();
  });
});