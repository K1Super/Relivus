import { useState } from 'react';
import { Checkbox, Input, Select, Typography } from 'antd';
import type { ColumnDetail, GenerationColumnConfig, TableDetail } from '@/types/api';

export const GENERATOR_OPTIONS: { value: string; label: string; hint: string }[] = [
  { value: 'random_int', label: 'random_int 随机整数', hint: '{"min":1,"max":999}' },
  { value: 'random_decimal', label: 'random_decimal 随机小数', hint: '{"min":0,"max":100,"scale":2}' },
  { value: 'faker', label: 'faker 假数据', hint: '{"provider":"name"}' },
  { value: 'enum', label: 'enum 枚举', hint: '{"values":["A","B"]}' },
  { value: 'fixed', label: 'fixed 固定值', hint: '{"value":"DEMO"}' },
  { value: 'regex', label: 'regex 正则', hint: '{"pattern":"\\\\d{4}-\\\\d{4}"}' },
  { value: 'timestamp', label: 'timestamp 时间戳', hint: '{"min":"2020-01-01","max":"2026-12-31"}' },
  { value: 'sequence', label: 'sequence 序号', hint: '{"start":1,"step":1}' },
  { value: 'ai', label: 'ai AI 生成', hint: '{"prompt":"生成18-65岁年龄，只输出数字"}' },
];

interface ColumnConfigFormProps {
  detail: TableDetail;
  value?: Record<string, GenerationColumnConfig>;
  onChange: (next: Record<string, GenerationColumnConfig>) => void;
}

/** 按数据类型推导默认生成器（首次勾选时预填，仍可调整）。 */
function defaultGeneratorFor(column: ColumnDetail): string {
  if (column.enumValues && column.enumValues.length > 0) {
    return 'enum';
  }
  const t = column.dataType.toLowerCase();
  if (t.includes('int') || t.includes('serial')) {
    return 'random_int';
  }
  if (t.includes('decimal') || t.includes('numeric') || t.includes('float') || t.includes('double')) {
    return 'random_decimal';
  }
  if (t.includes('date') || t.includes('time') || t.includes('timestamp')) {
    return 'timestamp';
  }
  return 'faker';
}

/** 列名旁的类型/属性徽标。 */
function columnMeta(detail: TableDetail, column: ColumnDetail): string {
  const flags: string[] = [];
  if (column.autoIncrement) {
    flags.push('自增');
  }
  if (detail.primaryKey === column.columnName) {
    flags.push('主键');
  }
  return flags.length > 0 ? `（${flags.join('，')}）` : '';
}

function Editor({
  column,
  config,
  onChange,
}: {
  column: ColumnDetail;
  config: GenerationColumnConfig;
  onChange: (next: GenerationColumnConfig) => void;
}) {
  const [paramsText, setParamsText] = useState(
    config.params ? JSON.stringify(config.params, null, 2) : '',
  );
  const [invalid, setInvalid] = useState(false);

  const hint = GENERATOR_OPTIONS.find(o => o.value === config.generator)?.hint ?? '';

  const applyParams = (text: string) => {
    setParamsText(text);
    if (text.trim() === '') {
      setInvalid(false);
      onChange({ ...config, params: undefined });
      return;
    }
    try {
      onChange({ ...config, params: JSON.parse(text) as Record<string, unknown> });
      setInvalid(false);
    } catch {
      setInvalid(true);
    }
  };

  return (
    <div data-testid={`column-editor-${column.columnName}`}>
      <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
        <Select
          aria-label={`列 ${column.columnName} 生成器`}
          style={{ width: 280 }}
          value={config.generator}
          options={GENERATOR_OPTIONS}
          onChange={generator => onChange({ ...config, generator })}
        />
      </div>
      <Input.TextArea
        aria-label="生成器参数 JSON"
        placeholder="参数 JSON，留空使用默认"
        autoSize={{ minRows: 2, maxRows: 6 }}
        className="mono-text"
        value={paramsText}
        status={invalid ? 'error' : undefined}
        onChange={e => applyParams(e.target.value)}
      />
      {invalid ? (
        <Typography.Text type="danger" style={{ fontSize: 12 }}>
          参数不是合法 JSON
        </Typography.Text>
      ) : hint ? (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          示例：{hint}
        </Typography.Text>
      ) : null}
    </div>
  );
}

/**
 * 列生成器配置（ColumnConfigForm）。
 * 默认全部列由引擎按 自增 > 外键 > ENUM > CHECK > 列名启发式 自动推断，
 * 勾选后可自定义生成器与参数。
 */
export function ColumnConfigForm({ detail, value = {}, onChange }: ColumnConfigFormProps) {
  const checkedNames = new Set(Object.keys(value));

  const toggle = (column: ColumnDetail, custom: boolean) => {
    const next = { ...value };
    if (custom) {
      next[column.columnName] = { generator: defaultGeneratorFor(column), params: {} };
    } else {
      delete next[column.columnName];
    }
    onChange(next);
  };

  return (
    <div>
      <Typography.Paragraph type="secondary" style={{ fontSize: 13 }}>
        未勾选的列由引擎按列类型、约束与列名自动推断生成器；勾选后人工指定。
      </Typography.Paragraph>
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <div style={{ minWidth: 320, flex: 1 }}>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            列清单（{detail.columns.length}）
          </Typography.Text>
          <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
            {detail.columns.map(column => (
              <Checkbox
                key={column.columnName}
                data-testid={`gen-toggle-${column.columnName}`}
                checked={checkedNames.has(column.columnName)}
                onChange={e => toggle(column, e.target.checked)}
              >
                <span className="mono-text">{column.columnName}</span>{' '}
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {column.dataType}
                  {columnMeta(detail, column)}
                </Typography.Text>
              </Checkbox>
            ))}
          </div>
        </div>
        <div style={{ minWidth: 360, flex: 2 }}>
          {Object.keys(value).length === 0 ? (
            <Typography.Text type="secondary" style={{ fontSize: 13 }}>
              未选择任何列，全部由引擎自动推断。
            </Typography.Text>
          ) : (
            Object.entries(value).map(([columnName, config]) => {
              const column = detail.columns.find(c => c.columnName === columnName);
              if (!column) {
                return null;
              }
              return (
                <div key={columnName} style={{ marginBottom: 16 }}>
                  <Typography.Text strong className="mono-text">
                    {columnName}
                  </Typography.Text>{' '}
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {column.dataType}
                  </Typography.Text>
                  <Editor
                    column={column}
                    config={config}
                    onChange={next => onChange({ ...value, [columnName]: next })}
                  />
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}