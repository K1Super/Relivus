import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { TableDetail } from '@/types/api';
import { isAuto, MaskingRuleForm, toRule } from '../MaskingRuleForm';

const usersTable: TableDetail = {
  tableName: 'users',
  primaryKey: 'id',
  columns: [
    { columnName: 'id', dataType: 'bigint', nullable: false, defaultValue: null, autoIncrement: true, enumValues: null },
    { columnName: 'phone', dataType: 'varchar', nullable: true, defaultValue: null, autoIncrement: false, enumValues: null },
  ],
  foreignKeys: [],
  checks: [],
};

describe('toRule 导出函数', () => {
  it('auto 时省略 algorithm 字段', () => {
    expect(toRule('auto', {})).toEqual({});
  });

  it('auto 时保留 columnGroup', () => {
    expect(toRule('auto', {}, 'grp-customer')).toEqual({ columnGroup: 'grp-customer' });
  });

  it('固定算法返回完整规则（含分组与版本）', () => {
    expect(toRule('fixed', { value: 'M' }, 'grp', 2)).toEqual({
      algorithm: 'fixed',
      params: { value: 'M' },
      columnGroup: 'grp',
      keyVersion: 2,
    });
  });

  it('keyVersion=1 时不携带 keyVersion 字段', () => {
    const rule = toRule('hmac', { algorithm: 'HmacSHA256' }, 'grp', 1);
    expect(rule.algorithm).toBe('hmac');
    expect(rule).not.toHaveProperty('keyVersion');
  });
});

describe('isAuto 导出函数', () => {
  it('未定义 / 空对象 / 显式 auto 均视为自动', () => {
    expect(isAuto(undefined)).toBe(true);
    expect(isAuto({})).toBe(true);
    expect(isAuto({ algorithm: 'auto' })).toBe(true);
  });

  it('指定算法时不视为自动', () => {
    expect(isAuto({ algorithm: 'phone' })).toBe(false);
  });
});

describe('MaskingRuleForm 组件', () => {
  it('勾选敏感列默认生成 auto 规则', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<MaskingRuleForm detail={usersTable} onChange={onChange} />);

    await user.click(screen.getByTestId('mask-toggle-phone'));

    expect(onChange).toHaveBeenCalledWith({
      table: 'users',
      columns: { phone: { algorithm: 'auto' } },
    });
  });

  it('取消勾选移除该列规则', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <MaskingRuleForm
        detail={usersTable}
        value={{ table: 'users', columns: { phone: { algorithm: 'auto' } } }}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByTestId('mask-toggle-phone'));

    expect(onChange).toHaveBeenCalledWith({ table: 'users', columns: {} });
  });

  it('未选择任何列时展示提示', () => {
    render(<MaskingRuleForm detail={usersTable} onChange={vi.fn()} />);

    expect(screen.getByText('未选择任何列，不生成脱敏规则。')).toBeInTheDocument();
  });
});