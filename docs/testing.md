# Relivus 测试说明（Testing Guide）

| 文档版本 | v1.0 |
| 更新日期 | 2026-09-18 |
| 状态 | 正式 |

本文件描述 Relivus 的测试策略、运行方式与关键测试清单。测试类清单均对照 `backend/src/test/java` 与 `frontend/src` 实际目录核对。接口契约见 [api-spec.md](api-spec.md)，错误码码表见 [api-spec.md](api-spec.md) 第 4 章。

## 1 测试策略

采用测试金字塔分层：底层大量单元测试，中层契约测试，上层少量集成测试与前端组件测试。

| 层级 | 工具 | 目标 |
|---|---|---|
| 单元测试 | JUnit 5 + Mockito + AssertJ | 纯逻辑：引擎、方言、生成器、脱敏算法、排序、解析 |
| 契约测试 | JUnit 5（无外部依赖） | 错误码结构、鉴权过滤器行为 |
| 集成测试 | JUnit 5 + Testcontainers | 双数据库（MySQL 8.0 / PostgreSQL 16）内省、生成、脱敏、任务、并发、迁移 |
| 前端测试 | Vitest + Testing Library | API 客户端、组件、Hook |

分层原则：

- 单元测试不依赖 Spring 容器与数据库，快速、可重复。
- 集成测试类以 `*IT.java` 命名，由 Failsafe 在 `verify` 阶段执行，共享 Testcontainers 容器。
- 集成测试基类 `AbstractDatabaseIT` 使用 `@Testcontainers(disabledWithoutDocker = true)`：Docker 不可用时整类自动跳过（assumption 失败按 skipped 处理），不阻塞构建。
- 部署层无任何 Docker 依赖，容器仅用于测试隔离目标库。

## 2 测试目录与命名约定

```text
backend/src/test/java/com/relivus/
├── unit 类：<Subject>Test.java          # 单元测试 + MockMvc（Surefire，mvn test）
├── integration/                        # 抽象基类 AbstractDatabaseIT.java
│   ├── *IT.java                        # 集成测试（Failsafe，mvn verify）
├── web/                                # TokenAuthFilterTest、TraceIdFilterTest
├── ai/                                 # RestClientAiHttpClientTest
└── ...

frontend/src/
├── api/__tests__/client.test.ts
├── components/__tests__/*.test.tsx
└── hooks/__tests__/*.test.ts
```

命名约定：

- 单元测试：与被测类同包同名 + `Test` 后缀（如 `UniqueConstraintCheckerTest`）。
- 集成测试：`IT` 后缀（如 `GenerationEngineIT`），统一置于 `integration` 包。
- 前端测试：与被测模块同目录的 `__tests__` 子目录，`.test.ts` / `.test.tsx` 后缀。

## 3 运行方式

### 3.1 后端

```bash
cd backend

# 仅单元测试 + MockMvc（Surefire 排除 *IT.java）
mvn test

# 完整校验：单元测试 + 集成测试（Testcontainers）+ JaCoCo 覆盖率门禁（verify phase）
mvn verify

# 跳过测试打包
mvn clean package -DskipTests
```

Java 版本 17、Maven 3.9+；集成测试依赖 Docker（`mysql:8.0` 与 `postgres:16` 镜像）。

覆盖率报告：`mvn test` 或 `mvn verify` 后在 test 阶段生成，位于：

```text
backend/target/site/jacoco/index.html
```

JaCoCo 覆盖率门禁在 `verify` 阶段强制执行：整体 BUNDLE 行覆盖率 `< 0.80` 时构建失败。

### 3.2 前端

```bash
cd frontend
npm ci

# 运行 Vitest
npm test              # 等价 vitest run

# 类型检查
npm run tsc -- --noEmit   # 或直接 npx tsc --noEmit

# 构建（先 tsc --noEmit 再 vite build）
npm run build
```

前端脚本定义于 `frontend/package.json`：`test` = `vitest run`、`build` = `tsc --noEmit && vite build`。

## 4 关键测试清单

### 4.1 契约与鉴权

| 测试类 | 覆盖点 |
|---|---|
| `common/exception/ErrorCodeContractTest` | 码值唯一、六位结构、首位 ∈ {1,5}、模块段已注册、服务端段级别必为 ERROR、责任方与 HTTP 区间一致 |
| `web/TokenAuthFilterTest` | 无 Token / 错误 Token 返回 401 + `100003`；正确 Token 放行 |
| `web/TraceIdFilterTest` | `X-Trace-Id` 透传 / 服务端生成，写入 MDC |

### 4.2 生成引擎与生成器

| 测试类 | 覆盖点 |
|---|---|
| `generator/ValueGeneratorUnitTest` | 各值生成器（random_int/random_decimal/faker/enum/fixed/regex/timestamp）输出类型与参数行为 |
| `generator/ValueGeneratorFactoryTest` | 列生成器解析优先级（用户配置 > 自增跳过 > 外键 > ENUM > CHECK > 主键 > 列名启发式 > 类型默认） |
| `generator/AiGeneratorTest` | AI 列级生成、批量缓存、上游失败降级为本地假数据、配置缺失原样上抛、类型规整 |
| `ai/RestClientAiHttpClientTest` | OpenAI 兼容协议 HTTP 客户端：请求构造、超时、4xx/5xx 处理 |
| `service/AiConfigServiceTest` | AI 配置 CRUD、单活跃切换、apiKey 加密存储出参不回传、连通性测试 |
| `generator/DataGenerationEngineTest` | 生成主流程：外键完整性、主键回填、唯一冲突重试、批处理 |
| `generator/BatchInserterTest` | 主键回填：插 N 行取 N ID 且顺序一致 |
| `generator/UniqueConstraintCheckerTest` | 本地 LRU 缓存、低基数冲突重试上限、高基数不 OOM |
| `generator/ForeignKeySamplerTest` | 外键采样策略 UNIFORM / ZIPF |
| `generator/TopologicalSorterTest` | 无环、有环、自引用 |
| `generator/TableDependencyGraphTest` | 表依赖图构建 |
| `schema/parser/CheckConstraintParserTest` | CHECK 约束解析：IN、范围、UNKNOWN |

### 4.3 方言与内省

| 测试类 | 覆盖点 |
|---|---|
| `dialect/MySQLDialectTest` / `PostgreSQLDialectTest` | 双方言标识符引用、类型映射、分页语法、INSERT 语法 |
| `dialect/DialectRegistryTest` | dbType → 方言注册解析 |
| `dialect/PaginationSyntaxTest` | 双方言分页 SQL 差异 |
| `dialect/DataTypeMappingTest` | JDBC 类型 ↔ 方言类型映射 |
| `schema/JdbcSchemaIntrospectorTest` | 表/列/主键/外键/唯一/CHECK/ENUM 内省 |
| `schema/SchemaCacheTest` | 元数据缓存一致性与过期 |
| `schema/model/TableMetadataTest` | 元数据模型 |

### 4.4 脱敏

| 测试类 | 覆盖点 |
|---|---|
| `masking/MaskingAlgorithmTest` | 各脱敏算法（fixed/regex/hmac/phone/id_card/bank_card/faker）格式保留与确定性 |
| `masking/MaskingEngineTest` | 脱敏主流程、跨表 JOIN 一致 |
| `masking/GlobalMaskingContextTest` | 映射缓存一致性与持久化回退 |
| `masking/JoinConsistencyVerifierTest` | 快照建表、比对、清理 |
| `masking/RelatedColumnDetectorTest` | 外键/同名列自动分组 |

### 4.5 任务与 SSE

| 测试类 | 覆盖点 |
|---|---|
| `task/TaskServiceTest` | 任务创建、状态机流转、取消（运行中 → CANCELLED）、清理 running/cancelFlags |
| `task/TaskStatusTest` | 状态机合法性 |
| `task/SseTaskProgressNotifierTest` | 事件格式（progress/log/done/error/heartbeat）、按 taskId 分组、断线清理 |
| `task/SseProgressControllerTest` | 进度端点：任务不存在 5001、连接注册、SSE 格式 |
| `repository/TaskRepositoryTest` / `TaskLogRepositoryTest` | df_task / df_task_log 持久化 |

### 4.6 集成测试（Testcontainers，双数据库）

| 测试类 | 场景 |
|---|---|
| `integration/SchemaIntrospectorIT` | 双数据库内省（表、列、主键、外键、唯一、CHECK、ENUM） |
| `integration/GenerationEngineIT` | 外键完整性、唯一无冲突、循环依赖可空成功 |
| `integration/CircularFkNotNullIT` | 循环依赖 FK 为 NOT NULL 抛 `130003` |
| `integration/MaskingEngineIT` | 跨表 JOIN 一致 |
| `integration/MaskMappingConcurrencyIT` | 映射表并发写：多线程同值仅落一条 |
| `integration/TaskCancelIT` | 任务运行中取消，状态变 CANCELLED |
| `integration/SseLimitIT` | SSE 超 20 连接拒绝（`150003`），心跳正常 |
| `integration/ThreadPoolRejectIT` | 线程池满抛 `150004` |
| `integration/DialectDifferenceIT` | 分页、标识符、类型映射差异 |
| `integration/FlywayMigrationIT` | 双数据库空库迁移 |

### 4.7 其他单元测试

| 测试类 | 覆盖点 |
|---|---|
| `util/LogMaskUtilTest` | 日志敏感信息脱敏 |
| `repository/ConnectionRepositoryTest` | 连接持久化（密码密文出参不回传） |
| `repository/AuditLogRepositoryTest` | 审计日志写入 |

### 4.8 前端测试

| 测试文件 | 覆盖点 |
|---|---|
| `api/__tests__/client.test.ts` | Axios 客户端：统一响应解析、Bearer 头、错误码 → ApiError、`999000` 网络兜底 |
| `hooks/__tests__/useTaskProgress.test.ts` | SSE 进度 Hook：事件解析、重连 |
| `components/__tests__/TaskProgress.test.tsx` | 任务进度组件渲染 |
| `components/__tests__/ProgressBar.test.tsx` | 进度条组件 |
| `components/__tests__/ColumnConfigForm.test.tsx` | 列生成器配置表单 |
| `components/__tests__/MaskingRuleForm.test.tsx` | 脱敏规则表单 |

## 5 覆盖率要求

- JaCoCo 行覆盖率门禁：`< 0.80` 构建失败（`verify` 阶段 `jacoco:check`）。
- 排除范围（`pom.xml` `jacoco-maven-plugin` excludes）：`RelivusApplication`、`dto/**`、`entity/**`、`config/**`、`common/**`、`security/**`、`controller/**`、`service/**`。
- 有效覆盖核心包：`dialect`、`schema`、`generator`、`masking`、`task`、`repository`、`ai`、`util`、`web` 等。
- 报告位置：`backend/target/site/jacoco/index.html`。

## 6 端到端演练

按真实接口与数据校验走通主链路（以 PostgreSQL 演示库 `relivus_demo` 为例，先执行 `demo/postgresql/init.sql`）。

```bash
# 1. 健康检查
curl -sf http://127.0.0.1:8080/actuator/health

# 2. 创建连接（返回连接 id，假设 2）
curl -s -X POST http://127.0.0.1:8080/api/connections \
  -H "Authorization: Bearer $RELIVUS_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"演示库","dbType":"postgresql","host":"127.0.0.1","port":5432,"database":"relivus_demo","username":"relivus","password":"change-me-strong"}'

# 3. 测试连接
curl -s -X POST http://127.0.0.1:8080/api/connections/2/test \
  -H "Authorization: Bearer $RELIVUS_TOKEN"

# 4. 内省表列表 / 依赖图
curl -s http://127.0.0.1:8080/api/schema/2/tables \
  -H "Authorization: Bearer $RELIVUS_TOKEN"
curl -s http://127.0.0.1:8080/api/schema/2/dependencies \
  -H "Authorization: Bearer $RELIVUS_TOKEN"

# 5. 预览生成（每表 ≤5 行真实插入）
curl -s -X POST http://127.0.0.1:8080/api/generation/preview \
  -H "Authorization: Bearer $RELIVUS_TOKEN" -H "Content-Type: application/json" \
  -d '{"connectionId":2,"tables":[{"table":"users","rowCount":10},{"table":"orders","rowCount":40}]}'

# 6. 执行生成任务（返回 taskId）
curl -s -X POST http://127.0.0.1:8080/api/generation/execute \
  -H "Authorization: Bearer $RELIVUS_TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-gen-001" \
  -d '{"connectionId":2,"tables":[{"table":"users","rowCount":1000},{"table":"orders","rowCount":5000}],"truncateBefore":true}'

# 7. 订阅进度 SSE（另开终端）
curl -N http://127.0.0.1:8080/api/tasks/42/progress \
  -H "Authorization: Bearer $RELIVUS_TOKEN" \
  -H "Accept: text/event-stream"

# 8. 取消（可选）
curl -s -X POST http://127.0.0.1:8080/api/tasks/42/cancel \
  -H "Authorization: Bearer $RELIVUS_TOKEN"

# 9. 任务详情
curl -s http://127.0.0.1:8080/api/tasks/42 \
  -H "Authorization: Bearer $RELIVUS_TOKEN"
```

数据质量校验 SQL（PostgreSQL，`relivus_demo` 库）：

```sql
-- 年龄区间：users.age 应落在 18–70
SELECT COUNT(*) AS out_of_range FROM users WHERE age < 18 OR age > 70;   -- 期望 0

-- 邮箱格式：email 应符合基础邮箱形态（demo 库 email 列为 UNIQUE 且 NOT NULL）
SELECT COUNT(*) AS bad_email
FROM users
WHERE email !~ '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$';      -- 期望 0

-- 唯一性：email 无重复
SELECT email, COUNT(*) FROM users GROUP BY email HAVING COUNT(*) > 1;    -- 期望空

-- 性别枚举：仅男/女
SELECT COUNT(*) AS bad_gender FROM users WHERE gender NOT IN ('男', '女'); -- 期望 0

-- 时间：created_at 不晚于当前时间
SELECT COUNT(*) AS future_rows FROM users WHERE created_at > now();        -- 期望 0

-- 余额：非负
SELECT COUNT(*) AS bad_balance FROM users WHERE balance < 0;               -- 期望 0

-- 姓名与性别一致：名字部分（去掉单字姓）应落在对应性别名库，不一致计数为 0
-- （男名库：伟/强/磊/军/洋/勇/杰/涛/明/超/平/刚/华/鹏/飞/宇/波/彬/浩/然/睿/晨/凯/翔/志强/浩然/俊杰/思远/文博）
-- （女名库：芳/娜/敏/静/丽/艳/娟/霞/婷/璐/洁/雪/欣/楠/桂/英/子涵/雨欣/嘉怡/雅静/诗涵）
SELECT COUNT(*) AS name_gender_mismatch FROM users WHERE NOT (
  (gender='男' AND SUBSTRING(name,2) IN ('伟','强','磊','军','洋','勇','杰','涛','明','超','平','刚','华','鹏','飞','宇','波','彬','浩','然','睿','晨','凯','翔','志强','浩然','俊杰','思远','文博'))
  OR (gender='女' AND SUBSTRING(name,2) IN ('芳','娜','敏','静','丽','艳','娟','霞','婷','璐','洁','雪','欣','楠','桂','英','子涵','雨欣','嘉怡','雅静','诗涵'))
);                                                                          -- 期望 0

-- 外键完整性：orders.user_id 都能找到 users.id
SELECT COUNT(*) AS orphan_orders
FROM orders o LEFT JOIN users u ON o.user_id = u.id
WHERE u.id IS NULL;                                                       -- 期望 0

-- 行数符合配置
SELECT (SELECT COUNT(*) FROM users) AS users_rows,
       (SELECT COUNT(*) FROM orders) AS orders_rows;
```

MySQL 等价写法：邮箱用 `email NOT REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'`；外键完整性查询同构。

## 7 测试数据与隔离要求

- 集成测试统一经 `AbstractDatabaseIT` 使用独立 Testcontainers 容器（MySQL 8.0 / PostgreSQL 16），库名 `relivus`、用户/密码 `relivus` / `relivus123`，与生产元库、演示目标库完全隔离。
- 每个测试自建 DDL 结构，测试结束随容器销毁，不污染共享实例。
- 单元测试不访问真实数据库；需持久化的组件（`TaskRepository` 等）使用测试替身或内存实现。
- 涉及密钥的测试使用测试专用密钥，禁止复用生产 `RELIVUS_AES_KEY` / `RELIVUS_HMAC_KEY` / `RELIVUS_TOKEN`。
- 脱敏测试数据不得包含真实 PII（个人信息），统一使用 DataFaker 或固定样例。
- MockMvc 鉴权测试固定校验无 Token / 错误 Token 两分支，验证 `401 + 100003`。

## 关联文档

- [api-spec.md](api-spec.md) — 接口契约与错误码（含 `ErrorCodeContractTest` 校验规则）
- [getting-started.md](getting-started.md) — 快速开始与环境准备
- [troubleshooting.md](troubleshooting.md) — 测试与运行常见问题排查