import { useState } from 'react';
import { Alert, Checkbox, Input, InputNumber, Select, Typography } from 'antd';
import type {
  MaskingColumnRule,
  MaskingTableRule,
  TableDetail,
} from '@/types/api';

export const MASKING_ALGORITHMS: {
  value: string;
  label: string;
  paramsHint: string;
  description: string;
  keyVersion?: boolean;
}[] = [
  { value: 'auto', label: '自动（按列名推断）', paramsHint: '无需参数，由引擎按列名启发式选择算法', description: '引擎根据列名（如 phone、id_card、email 等）自动选择最合适的脱敏算法。' },
  { value: 'fixed', label: 'fixed 固定值', paramsHint: '{"value":"MASKED"}', description: '整列替换为统一固定值，简单直接，但会丢失原始值之间的差异。' },
  { value: 'regex', label: 'regex 正则替换', paramsHint: '{"pattern":"\\\\d{6}(\\\\d{8})","replacement":"****$1"}', description: '按正则匹配并保留部分内容，适合需要保留结构特征的场景。' },
  { value: 'hmac', label: 'hmac 哈希（不可逆）', paramsHint: '{"algorithm":"HmacSHA256"}', keyVersion: true, description: '不可逆：输出为哈希串，数据无法还原，建议脱敏前备份。' },
  { value: 'phone', label: 'phone 手机号掩码', paramsHint: '无需参数', description: '保留前 3 位与后 4 位，中间以星号掩码，兼顾可读性。' },
  { value: 'id_card', label: 'id_card 身份证掩码', paramsHint: '无需参数', description: '仅保留前 6 位与后 4 位，中间以星号掩码。' },
  { value: 'bank_card', label: 'bank_card 银行卡掩码', paramsHint: '无需参数', description: '仅保留末 4 位用于对账，其余以星号掩码。' },
  { value: 'faker', label: 'faker 假数据', paramsHint: '{"provider":"name"}', description: '生成逼真的随机假数据，与原始值无关。' },
];

interface MaskingRuleFormProps {
  detail: TableDetail;
  value?: MaskingTableRule;
  onChange: (next: MaskingTableRule) => void;
}

function isAuto(rule: MaskingColumnRule | undefined): boolean {
  return rule == null || rule.algorithm == null || rule.algorithm === 'auto';
}

/** 生成提交时使用的规则（auto 时省略 algorithm，交由引擎推断）。 */
export function toRule(algorithm: string, params: Record<string, string>, columnGroup?: string, keyVersion?: number): MaskingColumnRule {
  if (algorithm === 'auto') {
    const base: MaskingColumnRule = {};
    if (columnGroup) {
      base.columnGroup = columnGroup;
    }
    return base;
  }
  return {
    algorithm,
    params,
    ...(columnGroup ? { columnGroup } : {}),
    ...(keyVersion && keyVersion > 1 ? { keyVersion } : {}),
  };
}

function Editor({
  rule,
  onChange,
}: {
  rule: MaskingColumnRule | undefined;
  onChange: (next: MaskingColumnRule) => void;
}) {
  const [paramsText, setParamsText] = useState(
    rule?.params && Object.keys(rule.params).length > 0 ? JSON.stringify(rule.params, null, 2) : '',
  );
  const [invalid, setInvalid] = useState(false);
  const algorithm = rule?.algorithm && rule.algorithm !== 'auto' ? rule.algorithm : 'auto';
  const meta = MASKING_ALGORITHMS.find(a => a.value === algorithm);

  const applyParams = (text: string) => {
    setParamsText(text);
    if (text.trim() === '') {
      setInvalid(false);
      const next = toRuleFrom(rule);
      delete next.params;
      onChange(next);
      return;
    }
    try {
      onChange({
        ...toRuleFrom(rule),
        params: JSON.parse(text) as Record<string, string>,
      });
      setInvalid(false);
    } catch {
      setInvalid(true);
    }
  };

  return (
    <div data-testid="masking-editor">
      <div style={{ display: 'flex', gap: 8, marginBottom: 8, flexWrap: 'wrap' }}>
        <Select
          aria-label="脱敏算法"
          style={{ width: 240 }}
          value={algorithm}
          options={MASKING_ALGORITHMS}
          onChange={algo =>
            onChange(
              toRule(algo, rule?.params ?? {}, rule?.columnGroup, rule?.keyVersion),
            )
          }
        />
        <Input
          aria-label="映射分组（可跨表一致）"
          style={{ width: 220 }}
          placeholder="映射分组（跨表一致用）"
          value={rule?.columnGroup ?? ''}
          onChange={e => onChange({ ...toRuleFrom(rule), columnGroup: e.target.value || undefined })}
        />
        {meta?.keyVersion && (
          <InputNumber
            aria-label="密钥版本"
            min={1}
            defaultValue={1}
            value={rule?.keyVersion ?? 1}
            onChange={v => onChange({ ...toRuleFrom(rule), keyVersion: v ?? 1 })}
          />
        )}
      </div>
      {meta?.description && (
        <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 8 }}>
          {meta.description}
        </Typography.Paragraph>
      )}
      {algorithm === 'hmac' && (
        <Alert
          type="warning"
          showIcon
          message="hmac 不可逆哈希"
          description="输出为哈希串，数据无法还原，建议脱敏前备份。"
          style={{ marginBottom: 8 }}
        />
      )}
      <Input.TextArea
        aria-label="算法参数 JSON"
        placeholder="参数 JSON，留空使用默认"
        autoSize={{ minRows: 2, maxRows: 5 }}
        className="mono-text"
        value={paramsText}
        status={invalid ? 'error' : undefined}
        onChange={e => applyParams(e.target.value)}
      />
      {invalid ? (
        <Typography.Text type="danger" style={{ fontSize: 12 }}>
          参数不是合法 JSON
        </Typography.Text>
      ) : meta && meta.paramsHint ? (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {meta.paramsHint}
        </Typography.Text>
      ) : null}
    </div>
  );
}

/** 由当前 rule/算法还原完整规则对象（Editor 内部合并用）。 */
function toRuleFrom(rule: MaskingColumnRule | undefined): MaskingColumnRule {
  const safe: MaskingColumnRule = { ...(rule ?? {}) };
  if (safe.algorithm === 'auto') {
    delete safe.algorithm;
  }
  return safe;
}

/**
 * 脱敏规则配置（DOC-07 MaskingRuleForm）。
 * 默认（不勾选）不脱敏；勾选后选择算法、参数与映射分组；表级 where 可选。
 */
export function MaskingRuleForm({ detail, value, onChange }: MaskingRuleFormProps) {
  const rule = value ?? { table: detail.tableName, columns: {} };
  const enabled = new Set(Object.keys(rule.columns));

  const toggle = (columnName: string, on: boolean) => {
    const columns = { ...rule.columns };
    if (on) {
      columns[columnName] = { algorithm: 'auto' };
    } else {
      delete columns[columnName];
    }
    onChange({ ...rule, columns });
  };

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <Typography.Text type="secondary" style={{ fontSize: 13 }}>
          过滤条件（可选，仅对满足条件的行脱敏，如 id &gt; 100）
        </Typography.Text>
        <Input
          style={{ marginTop: 4 }}
          placeholder="例如：id > 100（留空则脱敏全表）"
          value={rule.where ?? ''}
          onChange={e => onChange({ ...rule, where: e.target.value || undefined })}
        />
      </div>
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <div style={{ minWidth: 320, flex: 1 }}>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            敏感列（{detail.columns.length}）
          </Typography.Text>
          <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
            {detail.columns.map(column => (
              <Checkbox
                key={column.columnName}
                data-testid={`mask-toggle-${column.columnName}`}
                checked={enabled.has(column.columnName)}
                onChange={e => toggle(column.columnName, e.target.checked)}
              >
                <span className="mono-text">{column.columnName}</span>{' '}
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {column.dataType}
                </Typography.Text>
              </Checkbox>
            ))}
          </div>
        </div>
        <div style={{ minWidth: 380, flex: 2 }}>
          {Object.entries(rule.columns).length === 0 ? (
            <Typography.Text type="secondary" style={{ fontSize: 13 }}>
              未选择任何列，不生成脱敏规则。
            </Typography.Text>
          ) : (
            Object.entries(rule.columns).map(([columnName]) => {
              const enabledRule = rule.columns[columnName];
              return (
                <div key={columnName} style={{ marginBottom: 16 }}>
                  <Typography.Text strong className="mono-text">
                    {columnName}
                  </Typography.Text>
                  <div style={{ marginTop: 6 }}>
                    <Editor rule={enabledRule} onChange={next => onChange({ ...rule, columns: { ...rule.columns, [columnName]: next } })} />
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}

export { isAuto };