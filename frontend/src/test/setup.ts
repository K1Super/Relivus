import '@testing-library/jest-dom/vitest';

/**
 * jsdom 运行时补丁：
 * - ResizeObserver：React Flow / antd 局部组件依赖。
 * - matchMedia：antd 响应式 hook（Grid 等）依赖。
 * - scrollTo：部分组件调用。
 */

class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
(globalThis as any).ResizeObserver = ResizeObserverStub;

if (!window.matchMedia) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (window as any).matchMedia = (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  });
}

if (!window.scrollTo) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (window as any).scrollTo = () => {};
}