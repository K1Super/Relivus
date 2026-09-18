import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { TableDetail } from '@/types/api';
import { ColumnConfigForm } from '../ColumnConfigForm';

const usersTable: TableDetail = {
  tableName: 'users',
  primaryKey: 'id',
  columns: [
    { columnName: 'id', dataType: 'bigint', nullable: false, defaultValue: null, autoIncrement: true, enumValues: null },
    { columnName: 'name', dataType: 'varchar', nullable: true, defaultValue: null, autoIncrement: false, enumValues: null },
  ],
  foreignKeys: [],
  checks: [],
};

describe('ColumnConfigForm 组件', () => {
  it('勾选 varchar 列按类型推导为 faker 生成器', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<ColumnConfigForm detail={usersTable} onChange={onChange} />);

    await user.click(screen.getByTestId('gen-toggle-name'));

    expect(onChange).toHaveBeenCalledWith({
      name: { generator: 'faker', params: {} },
    });
  });

  it('勾选自增整数列推导为 random_int 生成器', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<ColumnConfigForm detail={usersTable} onChange={onChange} />);

    await user.click(screen.getByTestId('gen-toggle-id'));

    expect(onChange).toHaveBeenCalledWith({
      id: { generator: 'random_int', params: {} },
    });
  });

  it('取消勾选后移除对应列配置', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <ColumnConfigForm
        detail={usersTable}
        value={{ name: { generator: 'faker', params: {} } }}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByTestId('gen-toggle-name'));

    expect(onChange).toHaveBeenCalledWith({});
  });

  it('未选择任何列时展示自动推断提示', () => {
    render(<ColumnConfigForm detail={usersTable} onChange={vi.fn()} />);

    expect(screen.getByText('未选择任何列，全部由引擎自动推断。')).toBeInTheDocument();
  });
});