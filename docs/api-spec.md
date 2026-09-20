# Relivus 接口规范（API Specification）

| 文档版本 | v1.0 |
| 更新日期 | 2026-09-18 |
| 状态 | 正式 |

本文件为 Relivus 后端 REST 与 SSE 接口权威规范，路径与字段均已对照 `com.relivus.controller` 与 `com.relivus.dto` 实际代码核对。错误码权威码表见本文第 4 章。任务与 SSE 设计见 3.5 节，部署见 [deployment-guide.md](deployment-guide.md)。

## 1 概述

### 1.1 Base URL

后端默认端口 `8080`，所有业务接口以 `/api` 为前缀。开发环境前端 Vite 将 `/api` 代理到 `http://localhost:8080`；生产环境由 Nginx 将 `/api/` 反向代理到后端。

```text
开发： http://localhost:5173/api/...   （Vite 代理 → http://localhost:8080）
生产： http://<host>/api/...           （Nginx → http://127.0.0.1:8080）
```

OpenAPI 文档地址：`/v3/api-docs`（JSON）、`/swagger-ui.html`（UI）。健康检查：`/actuator/health`、`/actuator/info`（无需 Token）。

### 1.2 统一响应结构

所有接口返回统一结构 `ApiResponse<T>`，`code = 0` 表示成功：

| 字段 | 类型 | 说明 |
|---|---|---|
| code | int | 错误码，`0` 成功；非 0 见第 4 章错误码总表 |
| message | string | 提示信息，成功为 `success` |
| data | T | 业务数据，失败为 `null` |
| traceId | string | 请求级链路追踪 ID（源自 `X-Trace-Id` 请求头或服务端生成） |
| fieldErrors | array | 字段级错误明细，仅校验类错误（`100002`）非空 |

成功响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": { },
  "traceId": "b8f2a1c4e9d3",
  "fieldErrors": null
}
```

失败响应示例（参数校验）：

```json
{
  "code": 100002,
  "message": "参数校验失败，请检查填写内容",
  "data": null,
  "traceId": "b8f2a1c4e9d3",
  "fieldErrors": [
    { "field": "tables[0].rowCount", "message": "rowCount must be <= 500000" }
  ]
}
```

字段级明细结构 `FieldErrorDetail` 为 `{ "field": string, "message": string }`。`5xxxxx` 服务端故障码的 `message` 恒为通用提示，内部细节仅落服务端日志，不向客户端泄露。

### 1.3 鉴权

所有 `/api/**` 请求（含 SSE）必须携带 Bearer Token：

```http
Authorization: Bearer ${RELIVUS_TOKEN}
```

- 由 `OncePerRequestFilter`（`TokenAuthFilter`）统一校验，缺失或错误返回 HTTP 401 + 错误码 `100003`。
- 仅支持 `Authorization` 头；禁止 `X-Relivus-Token`、Cookie、query token 等旁路方式。

### 1.4 幂等

- `POST /api/generation/execute`、`POST /api/masking/execute` 支持 `Idempotency-Key` 请求头。
- 同一 `Idempotency-Key` 在 24 小时 TTL 内返回首次创建的任务 ID，防止重复提交。
- 幂等存储为进程内结构，语义适用于单实例部署；多实例横向扩展需改由元数据库承载（见部署文档）。

### 1.5 内容类型

- 普通接口：`Content-Type: application/json`、`Accept: application/json`。
- 任务进度：`GET /api/tasks/{id}/progress` 使用 `Accept: text/event-stream`（SSE）。

## 2 接口清单

### 2.1 连接管理

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| GET | /api/connections | 连接列表 | 无 |
| POST | /api/connections | 创建连接 | name/dbType/host/port/database/username/password |
| PUT | /api/connections/{id} | 更新连接 | 同创建 + updatePassword |
| DELETE | /api/connections/{id} | 删除连接 | 路径 id |
| POST | /api/connections/{id}/test | 测试已保存连接 | 路径 id |

### 2.2 Schema 内省

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| GET | /api/schema/{connId}/tables | 表列表 | 路径 connId |
| GET | /api/schema/{connId}/tables/{table} | 表详情 | 路径 connId/table |
| GET | /api/schema/{connId}/dependencies | 依赖图（拓扑序与循环） | 路径 connId |

### 2.3 数据生成

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| POST | /api/generation/preview | 预览生成（每表 ≤5 行真实插入） | connectionId/tables |
| POST | /api/generation/execute | 执行生成（异步任务） | connectionId/tables/samplingStrategy/truncateBefore/batchSize |

### 2.4 数据脱敏

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| POST | /api/masking/preview | 预览脱敏（不落库） | connectionId/tables |
| POST | /api/masking/execute | 执行脱敏（异步任务） | connectionId/tables/batchSize/verifyTables |
| POST | /api/masking/verify | 验证 JOIN 一致性（消费快照） | connectionId/joinSql/targetTable/pkColumn/joinKeyColumn |

### 2.5 任务管理

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| GET | /api/tasks | 任务列表 | limit（默认 20）/offset（默认 0） |
| GET | /api/tasks/{id} | 任务详情 | 路径 id |
| GET | /api/tasks/{id}/progress | 进度 SSE | 路径 id（事件流） |
| POST | /api/tasks/{id}/cancel | 取消任务 | 路径 id |
| GET | /api/tasks/{id}/tables | 生成数据回看：表清单 | 路径 id（仅 GENERATION + SUCCESS） |
| GET | /api/tasks/{id}/data | 生成数据回看：分页数据 | 路径 id + table/limit（≤200）/offset |

### 2.6 AI 模型配置

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| GET | /api/ai/configs | AI 配置列表 | 无 |
| POST | /api/ai/configs | 创建 AI 配置 | name/baseUrl/apiKey/model |
| PUT | /api/ai/configs/{id} | 更新 AI 配置 | 同创建，apiKey 留空不修改 |
| DELETE | /api/ai/configs/{id} | 删除 AI 配置 | 路径 id |
| POST | /api/ai/configs/{id}/activate | 激活 AI 配置（单活跃） | 路径 id |
| POST | /api/ai/configs/{id}/test | 测试 AI 配置连通性 | 路径 id |

## 3 接口详述

### 3.1 连接管理

`ConnectionController`，前缀 `/api/connections`。

#### 3.1.1 创建连接 `POST /api/connections`

请求示例：

```json
{
  "name": "本地目标库",
  "dbType": "postgresql",
  "host": "127.0.0.1",
  "port": 5432,
  "database": "relivus_target",
  "username": "relivus",
  "password": "change-me-strong"
}
```

关键参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | 是 | 连接名，唯一，≤128 |
| dbType | string | 是 | `mysql` / `postgresql` |
| host | string | 是 | 主机，≤255 |
| port | int | 是 | 端口，1–65535 |
| database | string | 是 | 数据库名 |
| username | string | 是 | 用户名 |
| password | string | 否 | 密码，仅入参，存储为 AES-GCM 密文，出参永不回传 |
| updatePassword | boolean | 否 | 更新场景是否显式重设密码（PUT 用） |

响应示例（`ConnectionResponse`，不含密码）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "name": "本地目标库",
    "dbType": "postgresql",
    "host": "127.0.0.1",
    "port": 5432,
    "database": "relivus_target",
    "username": "relivus",
    "createdAt": "2026-09-18T10:00:00",
    "updatedAt": "2026-09-18T10:00:00"
  },
  "traceId": null,
  "fieldErrors": null
}
```

可能错误码：`100002`（校验失败）、`100003`（鉴权失败）、`100005`（名称冲突）。

#### 3.1.2 测试已保存连接 `POST /api/connections/{id}/test`

响应示例（`ConnectionTestResult`）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "success": true,
    "message": "连接成功",
    "product": "PostgreSQL",
    "version": "16.3"
  },
  "traceId": null,
  "fieldErrors": null
}
```

可能错误码：`100004`（连接不存在）、`110001`（连接失败）、`110002`（不支持的数据库）。

更新/删除连接与创建共用一个请求体（更新时 `updatePassword=true` 或提供新 `password` 即为重设密码）。

### 3.2 Schema 内省

`SchemaController`，前缀 `/api/schema`。元数据经 `SchemaCache` 缓存（10 分钟）。

#### 3.2.1 表列表 `GET /api/schema/{connId}/tables`

响应示例（`SchemaTableSummary[]`）：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    { "tableName": "orders", "columnCount": 5, "primaryKey": "id" },
    { "tableName": "users", "columnCount": 7, "primaryKey": "id" }
  ],
  "traceId": null,
  "fieldErrors": null
}
```

#### 3.2.2 表详情 `GET /api/schema/{connId}/tables/{table}`

`data` 为 `TableDetailResponse`：`tableName`、`primaryKey`、`columns[]`（columnName/dataType/nullable/defaultValue/autoIncrement/enumValues）、`foreignKeys[]`（fkName/columnName/refTable/refColumn/nullable）、`checks[]`（constraintName/columns/type/allowedValues/minValue/maxValue）。

#### 3.2.3 依赖图 `GET /api/schema/{connId}/dependencies`

`data` 为 `DependencyGraphResponse`：`order`（拓扑序表名数组）、`cycles`（环数组）。

可能错误码：`110001`（连接失败）、`120001`（内省失败）、`100004`（连接不存在）。

### 3.3 数据生成

`GenerationController`，前缀 `/api/generation`。

#### 3.3.1 预览生成 `POST /api/generation/preview`

同步小批量试生成：每表行数强制收敛至 ≤5 行、强制不清空目标表，向目标库真实插入。前端需提示预览为有副作用操作。

请求示例（`GenerationConfig`）：

```json
{
  "connectionId": 2,
  "tables": [
    { "table": "users", "rowCount": 1000 },
    { "table": "orders", "rowCount": 5000 }
  ],
  "samplingStrategy": "UNIFORM",
  "truncateBefore": false,
  "batchSize": 1000
}
```

关键参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| connectionId | long | 是 | 目标库连接 ID |
| tables | array | 是 | 单表配置；`rowCount` 1–500000；`columns` 为列→生成器映射 |
| samplingStrategy | string | 否 | `UNIFORM` / `ZIPF`，默认 UNIFORM |
| truncateBefore | boolean | 否 | 生成前清空目标表，默认 false |
| batchSize | int | 否 | 批量插入大小，默认 1000 |

列生成器 `columns.<name>.generator` 取值：`random_int` / `random_decimal` / `faker` / `enum` / `fixed` / `regex` / `timestamp` / `foreign_key` / `sequence` / `ai`（`ai` 需 `params.prompt`）。

响应示例（`GenerationRunResult`）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "rowsByTable": { "users": 5, "orders": 5 },
    "elapsedMillis": 128
  },
  "traceId": null,
  "fieldErrors": null
}
```

#### 3.3.2 执行生成 `POST /api/generation/execute`

异步任务：创建 `df_task` 记录并提交线程池，进度经 SSE 推送。

请求体同 preview（`rowCount` 按实际值，上限 500000）。请求头可带 `Idempotency-Key`。

响应示例（`TaskResponse`）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 42,
    "taskType": "GENERATION",
    "connectionId": 2,
    "status": "PENDING",
    "progress": 0,
    "totalRows": 0,
    "processedRows": 0,
    "errorMessage": null,
    "cancelRequested": false,
    "createdAt": "2026-09-18T10:05:00",
    "startedAt": null,
    "finishedAt": null
  },
  "traceId": null,
  "fieldErrors": null
}
```

可能错误码：`100002`（校验/未知生成器）、`100003`、`110001`（连接失败）、`130002`（唯一约束冲突超限）、`130003`（循环依赖 FK NOT NULL）、`150003`（SSE 超限）、`150004`（队列满）、`160001`/`160002`（AI 配置缺失/上游失败）、`530001`（生成失败）。

### 3.4 数据脱敏

`MaskingController`，前缀 `/api/masking`。

#### 3.4.1 预览脱敏 `POST /api/masking/preview`

同步预览，不落库、不写映射表，每列最多返回 20 条样例。

请求示例（`MaskingTaskRequest`）：

```json
{
  "connectionId": 2,
  "tables": [
    {
      "table": "users",
      "columns": {
        "name": { "algorithm": "faker", "params": { "provider": "name" } },
        "email": { "algorithm": "hmac", "columnGroup": "email_grp", "keyVersion": 1 },
        "phone": { "algorithm": "phone" }
      },
      "where": "id > 0"
    }
  ],
  "batchSize": 1000
}
```

关键参数：

| 参数 | 类型 | 说明 |
|---|---|---|
| columns.<name>.algorithm | string | `fixed` / `regex` / `hmac` / `phone` / `id_card` / `bank_card` / `faker`；缺省按列名启发式 |
| columns.<name>.params | object | 算法参数 |
| columns.<name>.columnGroup | string | 映射分组；同组同原始值跨表结果一致；缺省按外键/同名列自动分组 |
| columns.<name>.keyVersion | int | 密钥版本（hmac），默认 1 |
| where | string | 过滤条件（仅限可信操作者） |

响应示例（`MaskingPreviewResponse`）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "rows": [
      { "table": "users", "column": "email", "original": "a@b.c", "masked": "x9f2...@example.com" }
    ]
  },
  "traceId": null,
  "fieldErrors": null
}
```

#### 3.4.2 执行脱敏 `POST /api/masking/execute`

异步任务。请求体同 preview，可带 `verifyTables`（在脱敏前为每张表建 JOIN 快照 `df_snap_<table>`），响应为 `TaskResponse`（taskType=MASKING）。请求头可带 `Idempotency-Key`。

`verifyTables` 元素（`VerifyTableSpec`）：`table`、`pkColumn`、`joinKeyColumn`、`whereClause`（可选），表名/列名仅允许字母数字下划线。

#### 3.4.3 验证 JOIN 一致性 `POST /api/masking/verify`

一次性消费脱敏任务生成的快照（比对后清理），重复校验需重新执行含 `verifyTables` 的脱敏任务。

请求示例（`JoinVerificationRequest`）：

```json
{
  "connectionId": 2,
  "joinSql": "SELECT oi.user_id FROM orders o JOIN order_items oi ON o.id = oi.order_id WHERE o.id = ?",
  "targetTable": "orders",
  "pkColumn": "id",
  "joinKeyColumn": "user_id",
  "whereClause": null
}
```

响应示例（`JoinVerificationResult`）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "checkedRows": 500,
    "mismatchRows": 0,
    "consistent": true,
    "message": "JOIN 一致性验证通过"
  },
  "traceId": null,
  "fieldErrors": null
}
```

可能错误码：`100002`、`140002`（快照不存在或验证不一致）、`540001`（脱敏失败）、`110001`。

### 3.5 任务管理

前置控制器为 `TaskController`（`/api/tasks`）与 `SseProgressController`（`GET /api/tasks/{id}/progress`）。

#### 3.5.1 任务列表 `GET /api/tasks?limit=&offset=`

`data` 为 `TaskResponse[]`。`TaskResponse` 字段：`id`、`taskType`（GENERATION/MASKING）、`connectionId`、`status`（PENDING/RUNNING/SUCCESS/FAILED/CANCELLED）、`progress`（0–100）、`totalRows`、`processedRows`、`errorMessage`、`cancelRequested`、`createdAt`、`startedAt`、`finishedAt`。

#### 3.5.2 任务详情 `GET /api/tasks/{id}`

`data` 为单个 `TaskResponse`。不存在返回 `150001`。

#### 3.5.3 取消任务 `POST /api/tasks/{id}/cancel`

`data` 为 `boolean`（是否取消成功）。仅运行中任务可取消，否则 `150002`。取消后引擎在每批处理后检查取消标志并退出，状态置为 `CANCELLED`。

#### 3.5.4 任务进度 SSE `GET /api/tasks/{id}/progress`

请求头须携带鉴权，前端用 `@microsoft/fetch-event-source`（原生 EventSource 无法携带 Authorization）。

```http
GET /api/tasks/{id}/progress
Accept: text/event-stream
Authorization: Bearer ${RELIVUS_TOKEN}
```

连接参数：超时 30 分钟；全局并发连接上限 20（超限 `150003`）；心跳每 15 秒一次；断线可重连。连接建立即推送一条 `log` 事件（SSE 连接已建立）。

事件类型与事件数据（`event:` 为事件名，`data:` 为 JSON）：

| 事件名 | 事件数据 | 说明 |
|---|---|---|
| progress | `{"taskId":1,"progress":50,"processed":5000,"total":10000}` | 进度推进 |
| log | `{"level":"INFO","message":"..."}` | 运行日志（level 为 INFO/WARN/ERROR） |
| done | `{"taskId":1,"status":"SUCCESS"}` | 任务完成（status 为 SUCCESS/FAILED/CANCELLED） |
| error | `{"taskId":1,"message":"..."}` | 任务失败 |
| heartbeat | `{}` | 心跳，每 15 秒 |

SSE 原始报文示例：

```text
event:progress
data:{"taskId":1,"progress":50,"processed":5000,"total":10000}

event:done
data:{"taskId":1,"status":"SUCCESS"}
```

#### 3.5.5 生成数据回看：表清单 `GET /api/tasks/{id}/tables`

仅 `GENERATION` + `SUCCESS` 任务可回看生成数据。`data` 为 `TaskGeneratedTable[]`：

| 字段 | 类型 | 说明 |
|---|---|---|
| table | string | 表名（任务生成范围内） |
| rowCount | int | 本次生成行数（配置值） |
| watermarkApplied | boolean | 是否可按主键基线精确筛选本次生成数据 |

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    { "table": "users", "rowCount": 1000, "watermarkApplied": true },
    { "table": "orders", "rowCount": 5000, "watermarkApplied": true }
  ],
  "traceId": null,
  "fieldErrors": null
}
```

可能错误码：`100003`（鉴权失败）、`150001`（任务不存在）、`150005`（任务未成功完成）、`100002`（非生成任务类型）。

#### 3.5.6 生成数据回看：分页数据 `GET /api/tasks/{id}/data`

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| table | string | 是 | 表名，须在该任务生成范围内 |
| limit | int | 否 | 每页行数，1–200，默认 100 |
| offset | int | 否 | 偏移，≥0，默认 0 |

`data` 为 `TaskDataPage`：

| 字段 | 类型 | 说明 |
|---|---|---|
| table | string | 表名 |
| columns | array | 列元信息：`name` / `type` |
| rows | array | 行数据，每行元素顺序与 `columns` 一致 |
| total | long | 满足基线条件的总行数 |
| limit / offset | int | 回显本次分页参数 |
| watermarkApplied | boolean | 是否按主键基线精确筛选本次数据 |

值归一化规则：`Timestamp` 输出 ISO-8601 字符串；`byte[]` 输出 `0x` 前缀十六进制；PG 自定义类型输出其文本表示。当表无数值主键或无基线时 `watermarkApplied=false`，按全表分页展示（前端提示"按全表展示"）。

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "table": "users",
    "columns": [
      { "name": "id", "type": "INT" },
      { "name": "name", "type": "VARCHAR" },
      { "name": "age", "type": "INT" },
      { "name": "email", "type": "VARCHAR" },
      { "name": "created_at", "type": "TIMESTAMP" }
    ],
    "rows": [
      [ 5001, "张伟", 28, "zhangwei@example.com", "2026-09-18T10:05:12" ],
      [ 5002, "李娜", 35, "lina@example.com", "2026-09-18T10:05:12" ]
    ],
    "total": 1000,
    "limit": 100,
    "offset": 0,
    "watermarkApplied": true
  },
  "traceId": null,
  "fieldErrors": null
}
```

可能错误码：`100003`、`150001`、`150005`、`100002`（表不在生成范围内 / 参数非法 / 目标表已不存在）、`110001`（目标库连接失败）。

### 3.6 AI 模型配置

`AiConfigController`，前缀 `/api/ai/configs`。

#### 3.6.1 创建 AI 配置 `POST /api/ai/configs`

请求示例（`CreateAiConfigRequest`）：

```json
{
  "name": "默认模型",
  "baseUrl": "https://api.openai.com/v1",
  "apiKey": "sk-...",
  "model": "gpt-4o-mini"
}
```

关键参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | 是 | 配置名，唯一，≤64 |
| baseUrl | string | 是 | OpenAI 兼容接口基址，≤255 |
| apiKey | string | 是 | 明文 Key，仅入参，AES-GCM 加密存储，出参永不回传；更新留空表示不修改 |
| model | string | 是 | 模型名，≤64 |

响应示例（`AiConfigResponse`，不含密钥）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "name": "默认模型",
    "baseUrl": "https://api.openai.com/v1",
    "model": "gpt-4o-mini",
    "active": false,
    "createdAt": "2026-09-18T10:00:00",
    "updatedAt": "2026-09-18T10:00:00"
  },
  "traceId": null,
  "fieldErrors": null
}
```

#### 3.6.2 激活 AI 配置 `POST /api/ai/configs/{id}/activate`

将目标配置置活跃并取消其他活跃配置，保证单活跃（生成时实时解析激活项）。

#### 3.6.3 测试 AI 配置连通性 `POST /api/ai/configs/{id}/test`

响应示例（`AiTestResponse`）：

```json
{
  "code": 0,
  "message": "success",
  "data": { "reachable": true, "detail": "pong: ..." },
  "traceId": null,
  "fieldErrors": null
}
```

可能错误码：`100002`、`100005`（重名）、`160001`（配置不存在）、`160002`（上游不可达）、`560001`（AI 服务内部故障）。

## 4 错误码总表

编码结构为六位三段式 `A-BB-CCC`：`A` 责任方（`1`=调用方可修正，`5`=服务端故障）；`BB` 模块域；`CCC` 模块内序号。以下码表自 `ErrorCode.java` 逐码核对，是本项目错误码的权威定义。

### 4.1 模块 00：通用

| 错误码 | 枚举名 | HTTP | 级别 | 含义 |
|---|---|---|---|---|
| 100002 | VALIDATION_FAILED | 400 | WARN | 参数校验失败（附 fieldErrors） |
| 100003 | AUTH_FAILED | 401 | WARN | Token 缺失/错误/失效 |
| 100004 | RESOURCE_NOT_FOUND | 404 | WARN | 目标资源不存在或已清理 |
| 100005 | RESOURCE_CONFLICT | 409 | WARN | 资源状态冲突 |
| 500001 | INTERNAL_ERROR | 500 | ERROR | 系统内部错误（兜底） |
| 500002 | CRYPTO_FAILED | 500 | ERROR | 密钥加解密失败 |

### 4.2 模块 10：连接

| 错误码 | 枚举名 | HTTP | 级别 | 含义 |
|---|---|---|---|---|
| 110001 | CONNECTION_FAILED | 502 | WARN | 目标库连接失败/密码错误/不可达 |
| 110002 | UNSUPPORTED_DATABASE | 400 | WARN | 数据库类型非 MySQL/PostgreSQL |

### 4.3 模块 20：Schema 内省

| 错误码 | 枚举名 | HTTP | 级别 | 含义 |
|---|---|---|---|---|
| 120001 | SCHEMA_INTROSPECTION_FAILED | 502 | WARN | 元数据读取失败/权限不足 |

### 4.4 模块 30：数据生成

| 错误码 | 枚举名 | HTTP | 级别 | 含义 |
|---|---|---|---|---|
| 130002 | UNIQUE_CONSTRAINT_EXCEEDED | 409 | WARN | 唯一约束冲突超限 |
| 130003 | CIRCULAR_FK_NOT_NULL | 409 | WARN | 循环依赖 FK 列为 NOT NULL |
| 530001 | GENERATION_FAILED | 500 | ERROR | 生成引擎/数据源系统故障 |

### 4.5 模块 40：数据脱敏

| 错误码 | 枚举名 | HTTP | 级别 | 含义 |
|---|---|---|---|---|
| 140002 | JOIN_VERIFICATION_FAILED | 409 | WARN | JOIN 一致性验证未通过 |
| 540001 | MASKING_FAILED | 500 | ERROR | 脱敏引擎系统故障 |

### 4.6 模块 50：任务

| 错误码 | 枚举名 | HTTP | 级别 | 含义 |
|---|---|---|---|---|
| 150001 | TASK_NOT_FOUND | 404 | WARN | 任务不存在/已清理 |
| 150002 | TASK_CANCEL_FAILED | 409 | WARN | 任务状态不允许取消 |
| 150003 | SSE_LIMIT_EXCEEDED | 429 | WARN | SSE 实时进度连接数超限 |
| 150004 | TASK_QUEUE_FULL | 429 | WARN | 任务执行队列已满 |
| 150005 | TASK_DATA_UNAVAILABLE | 409 | WARN | 任务未成功完成，生成数据不可回看 |

### 4.7 模块 60：AI

| 错误码 | 枚举名 | HTTP | 级别 | 含义 |
|---|---|---|---|---|
| 160001 | AI_CONFIG_NOT_FOUND | 404 | WARN | AI 配置不存在/未激活 |
| 160002 | AI_UPSTREAM_FAILED | 502 | WARN | 上游 AI 服务调用失败 |
| 560001 | AI_SERVICE_ERROR | 500 | ERROR | AI 服务内部系统故障 |

说明：`160001` / `160002` 在生成流程中的语义为「AI 配置缺失」与「上游失败」；`AiGenerator` 对上游运行时失败（非配置缺失）会降级为本地假数据并记录 WARN 日志，任务本身不失败；仅配置缺失（`160001`）时按整体失败处理。

## 5 前端错误码文案映射说明

前端在 `src/types/api.ts` 维护 `ERROR_CODE_TEXT` 映射表（码 → 用户可读中文提示），与后端 `ErrorCode` 枚举一一对应，禁止在前端另建散落映射。渲染逻辑见 `describeError`：

- 命中映射且 `code < 500000` 且非 `999000`：显示 `错误码 <code> · <文案>`；
- 命中映射且 `code >= 500000` 或 `code === 999000`：仅返回映射文案（服务端故障/网络层兜底不回显后端 message，避免泄露内部细节）；
- 未命中映射：`code >= 500000` 时返回通用文案 `系统内部错误，请稍后重试`，否则回显后端 `message`。

前端本地合成码使用 `99xxxx` 段（后端不得占用），当前仅 `999000`（网络层兜底：请求不可达/超时）。

新增错误码的标准链路：后端 `ErrorCode` 枚举注册 → 更新本文第 4 章码表 → 同步前端 `ERROR_CODE_TEXT` → 更新 `ErrorCodeContractTest` 结构校验。

## 关联文档

- [testing.md](testing.md) — 测试策略与关键测试清单
- [getting-started.md](getting-started.md) — 快速开始
- [deployment-guide.md](deployment-guide.md) — 部署指南