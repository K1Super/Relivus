/**
 * 领域类型 — 与后端 DTO 一一对应。
 */

/** 统一响应包裹（code=0 成功）。 */
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  /** 链路追踪 ID（错误响应携带）。 */
  traceId?: string;
  /** 字段级错误明细（校验类错误携带）。 */
  fieldErrors?: Array<{ field: string; message: string }>;
}

export type DbType = 'mysql' | 'postgresql';

export interface ConnectionResponse {
  id: number;
  name: string;
  dbType: DbType;
  host: string;
  port: number;
  database: string;
  username: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateConnectionRequest {
  name: string;
  dbType: DbType;
  host: string;
  port: number;
  database: string;
  username: string;
  /** 仅入参；后端出参永不回传密码 */
  password?: string;
  /** 更新场景是否显式重设密码 */
  updatePassword?: boolean;
}

export interface ConnectionTestResult {
  success: boolean;
  message: string;
  product: string;
  version: string;
}

/* ===== AI 模型配置 ===== */
export interface AiModelConfig {
  id: number;
  name: string;
  baseUrl: string;
  model: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AiConfigUpsertRequest {
  name: string;
  baseUrl: string;
  apiKey: string;
  model: string;
}

export interface AiConfigTestResult {
  reachable: boolean;
  detail: string;
}

export interface SchemaTableSummary {
  tableName: string;
  columnCount: number;
  primaryKey: string | null;
}

export interface ColumnDetail {
  columnName: string;
  dataType: string;
  nullable: boolean;
  defaultValue: string | null;
  autoIncrement: boolean;
  enumValues: string[] | null;
}

export interface ForeignKeyDetail {
  fkName: string;
  columnName: string;
  refTable: string;
  refColumn: string;
  nullable: boolean;
}

export interface CheckDetail {
  constraintName: string;
  columns: string[];
  type: string;
  allowedValues: string[] | null;
  minValue: number | null;
  maxValue: number | null;
}

export interface TableDetail {
  tableName: string;
  primaryKey: string | null;
  columns: ColumnDetail[];
  foreignKeys: ForeignKeyDetail[];
  checks: CheckDetail[];
}

export interface DependencyGraph {
  order: string[];
  cycles: string[][];
}

/* ===== 生成 ===== */
export interface GenerationColumnConfig {
  generator: string;
  params?: Record<string, unknown>;
}

export interface GenerationTableConfig {
  table: string;
  rowCount: number;
  columns?: Record<string, GenerationColumnConfig>;
}

export type SamplingStrategy = 'UNIFORM' | 'ZIPF';

export interface GenerationConfig {
  connectionId: number;
  tables: GenerationTableConfig[];
  samplingStrategy?: SamplingStrategy;
  truncateBefore?: boolean;
  batchSize?: number;
}

export interface GenerationRunResult {
  rowsByTable: Record<string, number>;
  elapsedMillis: number;
}

/* ===== 脱敏 ===== */
export type MaskingAlgorithm =
  | 'fixed'
  | 'regex'
  | 'hmac'
  | 'phone'
  | 'id_card'
  | 'bank_card'
  | 'faker'
  | 'auto';

export interface MaskingColumnRule {
  algorithm?: string;
  params?: Record<string, string>;
  columnGroup?: string;
  keyVersion?: number;
}

export interface MaskingTableRule {
  table: string;
  columns: Record<string, MaskingColumnRule>;
  where?: string;
}

export interface VerifyTableSpec {
  table: string;
  pkColumn: string;
  joinKeyColumn: string;
  whereClause?: string;
}

export interface MaskingTaskRequest {
  connectionId: number;
  tables: MaskingTableRule[];
  batchSize?: number;
  verifyTables?: VerifyTableSpec[];
}

export interface PreviewRow {
  table: string;
  column: string;
  original: string;
  masked: string;
}

export interface MaskingPreviewResponse {
  rows: PreviewRow[];
}

export interface JoinVerificationRequest {
  connectionId: number;
  joinSql: string;
  targetTable: string;
  pkColumn: string;
  joinKeyColumn: string;
  whereClause?: string;
}

export interface JoinVerificationResult {
  checkedRows: number;
  mismatchRows: number;
  consistent: boolean;
  message: string;
}

/* ===== 任务 ===== */
export type TaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';
export type TaskType = 'GENERATION' | 'MASKING';

export interface TaskResponse {
  id: number;
  taskType: TaskType;
  connectionId: number;
  status: TaskStatus;
  progress: number;
  totalRows: number;
  processedRows: number;
  errorMessage: string | null;
  cancelRequested: boolean;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

/* ===== 生成数据回看（任务成功后可视化本次生成的行） ===== */
export interface TaskGeneratedTable {
  table: string;
  rowCount: number;
  /** 是否可按「主键 > 基线」精确筛选本次生成行（false 表示无数值主键，回看全表） */
  watermarkApplied: boolean;
}

export interface TaskDataColumn {
  name: string;
  type: string;
}

export interface TaskDataPage {
  table: string;
  columns: TaskDataColumn[];
  /** 行数据：内层数组顺序与 columns 一致 */
  rows: unknown[][];
  total: number;
  limit: number;
  offset: number;
  watermarkApplied: boolean;
}

/* ===== SSE 事件 ===== */
export interface SseProgressEvent {
  taskId: number;
  progress: number;
  processed: number;
  total: number;
}

export interface SseLogEvent {
  level: 'INFO' | 'WARN' | 'ERROR';
  message: string;
}

export interface SseDoneEvent {
  taskId: number;
  status: string;
}

export interface SseErrorEvent {
  taskId: number;
  message: string;
}

/* ===== 错误码 → 用户提示 ===== */
export const ERROR_CODE_TEXT: Record<number, string> = {
  100002: '参数校验失败，请检查填写内容',
  100003: '鉴权失败，请到设置页填写 Token',
  100004: '资源不存在或已被删除',
  100005: '当前状态冲突，请刷新后重试',
  110001: '目标库连接失败，请检查连接配置',
  110002: '不支持的数据库类型，仅支持 MySQL / PostgreSQL',
  120001: 'Schema 内省失败，请确认表结构可读',
  130002: '唯一约束冲突超限，已停止生成',
  130003: '循环依赖的外键列不允许为空，需先建立初始行',
  140002: 'JOIN 一致性验证失败，跨表脱敏结果不一致',
  150001: '任务不存在或已被清理',
  150002: '当前状态无法取消任务（仅运行中可取消）',
  150003: '实时进度连接数超限，请稍后重试',
  150004: '任务队列已满，请稍后重试',
  150005: '任务未成功完成，无法查看生成数据',
  160001: 'AI 配置不存在或已被删除',
  160002: 'AI 服务调用失败，请检查配置或网络',
  500001: '系统内部错误，请稍后重试',
  500002: '系统内部错误，请稍后重试',
  530001: '数据生成失败，请稍后重试',
  540001: '数据脱敏失败，请稍后重试',
  560001: 'AI 服务内部错误，请稍后重试',
  999000: '网络异常，请检查服务是否可用',
};

export interface ApiFault {
  code: number;
  message: string;
}

/** 从任一错误形态提取用户可读提示（含错误码）。 */
export function describeError(err: unknown): string {
  if (err instanceof ApiError) {
    const hint = ERROR_CODE_TEXT[err.code];
    if (err.code >= 500000 || err.code === 999000) {
      // 服务端故障 / 网络层错误：直接返回映射文案，不暴露内部细节。
      return hint ?? '系统内部错误，请稍后重试';
    }
    return hint ? `错误码 ${err.code} · ${hint}` : `错误码 ${err.code} · ${err.message}`;
  }
  if (err instanceof Error) {
    return err.message;
  }
  return '发生未知错误，请重试';
}

/** 业务/鉴权错误，携带后端 ApiResponse 错误码。 */
export class ApiError extends Error {
  readonly code: number;
  readonly traceId?: string;
  readonly fieldErrors?: Array<{ field: string; message: string }>;

  constructor(
    code: number,
    message: string,
    traceId?: string,
    fieldErrors?: Array<{ field: string; message: string }>,
  ) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.traceId = traceId;
    this.fieldErrors = fieldErrors;
  }
}