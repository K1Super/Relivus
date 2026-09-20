# Relivus 编码规范（Coding Standards）

| 文档版本 | v1.0 | 更新日期 | 2026-09-18 | 状态 | 正式 |
|---|---|---|---|---|---|

> 本规范整合并取代以下旧文档：`00-standards.md`、`Enterprise General SE Standards.md`、`Enterprise_FullStack_Unified_Logging_Specification.md`、`Entry File Architecture Specification.md`、`UI Development Specification.md`。规范冲突时一律以本规范为准。
>
> 目标：新人拿本规范 + 设计文档即可合格提交代码。

## 1 总则

### 1.1 安全合规优先级
1.1.1 安全与合规优先级高于功能、性能与美观，任何实现不得以牺牲安全为代价。

1.1.2 密钥、凭据、连接密码仅通过环境变量注入，禁止硬编码到代码或配置文件。

1.1.3 禁止在代码、配置文件、日志、测试样本中提交任何真实密钥、密码、Token。

1.1.4 配置键统一使用 `relivus.*` 前缀，读取 `RELIVUS_TOKEN`、`RELIVUS_AES_KEY`、`RELIVUS_HMAC_KEY` 三个环境变量。

### 1.2 密码与密码学
1.2.1 禁止使用 MD5、SHA-1 及任何弱哈希算法用于密码或数据签名。

1.2.2 禁止以明文存储密码；密码存储用加盐不可逆哈希，连接密码用 AES-GCM 256 加密（Base64 32 字节密钥）。

1.2.3 脱敏 HMAC 统一用 HMAC-SHA256；禁止混用 `HMAC_KEY`、`RELIVUS_SECRET_KEY` 等旧密钥名。

### 1.3 输入校验与净化
1.3.1 所有外部输入（HTTP 参数、JSON 体、导入文件内容、数据库内省结果）必须校验合法性：非空、长度、格式、范围。

1.3.2 后端 DTO 一律使用 `@Valid` + Jakarta Validation 注解做参数校验。

1.3.3 数据库访问一律参数绑定，禁止字符串拼接 SQL。

1.3.4 对外输出与日志在渲染前必须净化，杜绝 XSS 与日志注入。

### 1.4 错误码
1.4.1 所有业务错误码必须通过 `ErrorCode` 枚举集中注册，禁止裸字符串、魔法数字错误码。

1.4.2 错误码采用六位三段式 `A-BB-CCC`（责任方-模块-序号），码值一经发布冻结，新增必须同步 `ErrorCode` 枚举、`docs/api-spec.md` 第 4 章码表与前端 `ERROR_CODE_TEXT`。

1.4.3 所有异常继承 `RelivusException` 且携带错误码；`1xxxxx` 为调用方可修正，`5xxxxx` 为服务端故障（对外统一通用提示）。

### 1.5 技术栈冻结
1.5.1 技术栈冻结为：Java 17 + Spring Boot 3.4.x + Spring JDBC（禁 JPA）、React 18 + TypeScript + Vite + Ant Design 5。

1.5.2 依赖冻结为：Flyway 10.x、Caffeine 3.x、JGraphT 1.5.x、DataFaker 2.x、TanStack Query、Zustand、React Flow。

1.5.3 异步统一 `ThreadPoolTaskExecutor`，禁止虚拟线程；测试统一 JUnit5 + Mockito + AssertJ + MockMvc + Testcontainers + Vitest。

1.5.4 新增依赖/框架须经评审，禁止引入无 LTS、已停止维护的快照版依赖。

### 1.6 部署红线
1.6.1 部署为裸机 / 本机服务 / systemd + Nginx，禁止生成任何 Docker、Dockerfile、docker-compose、容器部署内容。

1.6.2 Testcontainers 仅用于测试，部署层完全无 Docker。

## 2 后端工程规范

### 2.1 包结构与分层
2.1.1 包根为 `com.relivus`，顶层按 `config / dialect / schema / generator / masking / task / controller / dto / entity / repository / util` 组织。

2.1.2 分层职责边界：`controller` 只做参数接收、校验、调用 service、封装返回；`service` 承载业务逻辑、事务、状态流转；`repository` 只做数据 CRUD；`generator` 生成逻辑；`masking` 脱敏逻辑；`schema` 内省逻辑。

2.1.3 调用链固定为 `Controller → Service(接口) → ServiceImpl → Repository → 数据库`，禁止跨层调用、禁止 Controller 直连 Repository、禁止 Repository 反向调用 Service。

2.1.4 唯一启动入口 `RelivusApplication` 只做装配，不含任何业务逻辑。

### 2.2 命名约定
2.2.1 控制层 `XxxController`，业务接口 `IXxxService`，实现 `XxxServiceImpl`，数据访问 `XxxRepository`。

2.2.2 实体 `XxxEntity`，入参 `XxxDTO`，出参 `XxxVO`/`ApiResponse<T>`。

2.2.3 方法命名动词+名词：`getById / list / pageList / save / update / delete / cancel` 等。

2.2.4 布尔变量语义命名（`isXXX / hasXXX`），集合统一后缀 `List / Map / Set`，常量全大写下划线。

2.2.5 所有 DTO 用 `record`（Java 17），所有接口方法写 Javadoc。

### 2.3 API 与异常
2.3.1 所有 API 返回 `ApiResponse<T>` 统一结构（`code / message / data`），响应统一携带 `traceId` 与校验明细 `fieldErrors`。

2.3.2 全部 `/api/**` 使用 `Authorization: Bearer ${RELIVUS_TOKEN}`，后端 `OncePerRequestFilter` 统一校验。

2.3.3 全局异常处理器接管所有异常，Controller 禁止手写 try-catch。

2.3.4 生产环境只向前端返回友好提示与错误码，完整堆栈仅留日志，不对外暴露。

2.3.5 校验类错误附 `fieldErrors` 字段明细；`/execute`、`/preview`、`/verify` 支持 `Idempotency-Key` 幂等。

### 2.4 校验与事务
2.4.1 出入参校验全覆盖：空值、长度、格式、范围、业务合法性。

2.4.2 事务统一在 Service 层用 `@Transactional` 声明，事务只覆盖核心业务原子性，禁止大事务、长事务，禁止事务内包含无关查询、远程调用、日志。

2.4.3 禁止空 catch 吞异常，所有异常必须记录完整堆栈与业务上下文。

### 2.5 并发约束
2.5.1 值生成器必须无共享可变状态：生成逻辑基于不可变输入 + 线程安全的局部上下文。

2.5.2 唯一冲突检测双层兜底：数据库唯一约束 + 本地 Caffeine LRU 缓存（最近 10000 个值）。

2.5.3 唯一冲突阈值：普通列最大重试 100 次；低基数列 10 次仍冲突抛 `130002`（UNIQUE_CONSTRAINT_EXCEEDED）。

2.5.4 并发写入映射表统一封装 `MaskMappingRepository`：MySQL 用 `INSERT IGNORE`，PG 用 `INSERT ... ON CONFLICT DO NOTHING`。

2.5.5 并行只用于无依赖表，外键采样分页或蓄水池采样，禁止全量加载父表。

### 2.6 审计
2.6.1 敏感操作（连接增删改、脱敏执行、数据导出、任务取消）必须写审计日志 `df_audit_log`。

2.6.2 审计日志使用独立日志流，携带 `audit_type / operator / target_resource / result`，不与业务日志混存。

2.6.3 任务状态流转统一状态机 `PENDING → RUNNING → SUCCESS / FAILED / CANCELLED`，取消时更新 `df_task.cancel_requested = true`。

## 3 日志规范

### 3.1 统一分级
3.1.1 统一五级日志：TRACE、DEBUG、INFO、WARN、ERROR（主线业务为 DEBUG/INFO/WARN/ERROR，TRACE 仅本地调试）。

3.1.2 INFO 记录核心正常业务节点（登录、数据增改、接口成功、任务完成）；WARN 记录降级、重试成功、参数兼容、队列高水位。

3.1.3 ERROR 记录业务失败、系统异常、未捕获异常，必须携带完整堆栈、错误码与业务上下文。

3.1.4 生产环境关闭 TRACE/DEBUG，仅 INFO/WARN/ERROR；禁止级别混用（正常业务打 ERROR、异常打 INFO）。

### 3.2 结构化字段
3.2.1 生产日志强制 JSON 输出，字段统一 `snake_case`，时间戳 RFC3339 毫秒精度，对齐 OpenTelemetry 模型。

3.2.2 每条日志必带公共字段：`timestamp / env / service / level / action`。

3.2.3 链路追溯字段强制：`trace_id / span_id`（`trace_id` 符合 W3C Trace Context 全链路透传），辅以 `parent_span_id / trace_flags / path / duration`。

3.2.4 ERROR 级别必填：`error_message / stack_trace / error_location / error_code`。

3.2.5 业务身份字段有则必带：`user_id`（假名化/哈希）、`client_ip / server_ip / biz_no`。

### 3.3 敏感数据脱敏红线
3.3.1 禁止裸打印密码、Token、apiKey、Authorization、密钥、Cookie、SessionId 明文，一律完全掩码（`******`）。

3.3.2 PII 部分保留掩码：手机号前 3 后 4（`138****1234`）、身份证前 6 后 4、银行卡仅后 4 位、邮箱仅首字母 + 域名。

3.3.3 禁止打印完整请求体/响应体原始对象，禁止在异常/调试日志输出用户隐私或密钥。

3.3.4 禁止输出可定位自然人的原始证件号、生物特征；用户标识、业务单号须加盐哈希或假名化。

### 3.4 输出与性能
3.4.1 禁止裸 `System.out.println`、`printStackTrace`、`console.log/console.error`，统一走封装日志工具。

3.4.2 日志必须异步非阻塞输出（Log4j2 AsyncAppender / Disruptor），队列使用率 80% 触发采样告警，满载丢弃非 ERROR 或阻塞调用线程最多 100ms。

3.4.3 禁止在循环、高频回调、批量处理中直接打印日志，用采样计数器每 N 次或每 5 分钟汇总一条。

3.4.4 异步线程与回调结束必须 `try-finally` 清理 MDC，防止线程复用残留上次请求的 `user_id`。

3.4.5 日志文案句式统一「动作 + 业务对象 + 执行结果/异常原因」，禁止无上下文短句（如仅"出错了"）。

## 4 前端工程规范

### 4.1 目录结构
4.1.1 前端目录固定：`src/api`（接口封装）、`src/components`（组件）、`src/pages`（页面）、`src/hooks`（组合式逻辑）、`src/stores`（Zustand）、`src/types`（TS 类型）、`src/utils`（工具）、`src/styles`（全局/令牌样式）。

4.1.2 路由共六页面：`/connections`、`/schema/:connId`、`/generation`、`/masking`、`/tasks` 与 `/settings`（设置页承载鉴权 Token 与 AI 模型配置）。

4.1.3 组件区分公共组件（common）与业务组件（business），页面与组件职责分离：页面只编排，具体交互下沉到组件与 hooks。

### 4.2 入口文件装配
4.2.1 入口文件（`main.tsx`）只做装配：渲染根组件、注册全局 ErrorBoundary、挂载请求拦截、状态初始化、DOM 挂载。

4.2.2 入口禁止写业务逻辑、接口请求、页面渲染、判断分支、硬编码 IP/密钥。

4.2.3 请求拦截、路由守卫、全局异常捕获抽离独立文件，入口仅引入初始化。

### 4.3 状态管理
4.3.1 TanStack Query 统一管理服务端状态（查询、缓存、失效、轮询），Zustand 统一管理本地 UI 状态。

4.3.2 禁止在组件内直接写 fetch/axios 请求逻辑，服务端数据一律走 TanStack Query hooks。

### 4.4 API 封装
4.4.1 Axios 统一实例 `baseURL: '/api'`，请求拦截器统一注入 `Authorization: Bearer <token>`。

4.4.2 Token 统一从 `localStorage` 的 `relivusToken` 键读取；Axios 与 SSE 共用同一来源；`VITE_RELIVUS_TOKEN` 仅本地开发使用，不进生产构建。

4.4.3 接口按模块拆分封装（`connection.ts / task.ts / generation.ts / masking.ts / schema.ts`），禁止在页面内散落请求。

4.4.4 统一错误码文案映射集中维护于 `ERROR_CODE_TEXT`，401 统一提示并引导到设置页。

### 4.5 SSE
4.5.1 SSE 统一使用 `@microsoft/fetch-event-source`，禁用原生 `EventSource`（无法携带 Authorization）。

4.5.2 SSE 统一路径 `/api/tasks/{id}/progress`，事件固定 `progress / log / done / error / heartbeat`，断线由库自动重连、组件卸载时 AbortController 主动断开。

## 5 UI 设计规范

### 5.1 设计哲学
5.1.1 顶级美学 = 秩序 × 克制 × 精确 × 性能，用高级白灰黑配色与简约布局表达信息层级，而非装饰。

5.1.2 美学不得牺牲性能，性能本身即美学，长时间使用不疲劳为第一目标。

### 5.2 色板与用色
5.2.1 只用四种语义色 + 一套灰阶，Light/Dark 双主题下表取值：

| 令牌 | Light | Dark | 用途 |
|---|---|---|---|
| `color-primary` | `#1677ff` | `#3c89ff` | 主操作、链接、选中 |
| `color-success` | `#52c41a` | `#73d13d` | 成功、完成 |
| `color-warning` | `#faad14` | `#ffc53d` | 警告 |
| `color-error` | `#ff4d4f` | `#ff7875` | 错误、危险 |
| `gray-50` | `#fafafa` | `#0a0a0a` | 背景 |
| `gray-100` | `#f5f5f5` | `#141414` | 卡片 |
| `gray-200` | `#e8e8e8` | `#1f1f1f` | 分割线 |
| `gray-300` | `#d9d9d9` | `#303030` | 边框 |
| `gray-400` | `#bfbfbf` | `#434343` | 禁用 |
| `gray-500` | `#8c8c8c` | `#595959` | 辅助 |
| `gray-600` | `#595959` | `#737373` | 次要 |
| `gray-700` | `#333333` | `#a6a6a6` | 正文 |
| `gray-800` | `#1f1f1f` | `#e6e6e6` | 标题 |
| `gray-900` | `#0a0a0a` | `#fafafa` | 强调 |

5.2.2 禁止纯黑 `#000` 正文/背景与纯白对比，禁止彩色阴影，禁止渐变文字、多层阴影叠加、无意义发光。

5.2.3 对比度硬线：正文 4.5:1，大文本（≥18px）与图标、边框 3:1。

5.2.4 所有颜色通过 `tokens.css` 的 CSS 变量引用，禁止硬编码色值。

### 5.3 字体
5.3.1 只用两套字体：UI 文本用系统字体族（`system-ui, -apple-system, Segoe UI, PingFang SC, Microsoft YaHei`），等宽文本用 `JetBrains Mono / SF Mono / Consolas` 等宽族；数字对齐 `tabular-nums`。

5.3.2 字号只有六档：

| 令牌 | 字号 | 行高 | 用途 |
|---|---|---|---|
| `font-xs` | 12px | 18px | 辅助信息 |
| `font-sm` | 13px | 18px | 日志、次要 |
| `font-base` | 14px | 22px | 正文 |
| `font-md` | 16px | 24px | 强调 |
| `font-lg` | 20px | 28px | 小标题 |
| `font-xl` | 24px | 32px | 页面标题 |

5.3.3 字重只用 400 / 500 / 600 三档（正文/强调/标题），禁止 300 以下、700 以上。

### 5.4 间距
5.4.1 布局尺寸必须 8 的倍数（8/16/24/32/40/48/64/80/96）；组件内部尺寸允许 4 的倍数（4/8/12/16/20/24）。

5.4.2 页面边缘 24px，同组内元素 8px，同组不同项 16px，不同组 24px，不同区块 32px。

### 5.5 圆角与阴影
5.5.1 圆角只用四档：0px（表格、输入框）、4px（按钮、卡片）、8px（对话框、面板）、9999px（头像、胶囊）。

5.5.2 阴影只用三档：`shadow-sm 0 1px 2px / shadow-md 0 4px 8px / shadow-lg 0 8px 24px`（rgba 黑 0.06/0.08/0.12），禁止彩色阴影与多层叠加。

### 5.6 层级
5.6.1 z-index 只用下表七档，禁止随意取任意值：

| 令牌 | z-index | 用途 |
|---|---|---|
| `z-base` | 0 | 默认 |
| `z-sticky` | 10 | 吸顶 |
| `z-dropdown` | 100 | 下拉 |
| `z-popover` | 200 | 气泡 |
| `z-modal` | 1000 | 模态 |
| `z-toast` | 2000 | 全局提示 |
| `z-tooltip` | 3000 | 工具提示 |

### 5.7 按钮
5.7.1 按钮类型四档：Primary（主操作，一屏最多 1）、Secondary（次要，最多 3）、Tertiary（辅助）、Danger（危险，最多 1）。

5.7.2 按钮尺寸三档：Small 24px / Medium 32px / Large 40px，字号对应 12/14/16。

5.7.3 禁止黑色按钮（主按钮必须使用 `color-primary`），禁止按钮文案超过 6 个汉字或文字换行，禁止一屏出现两个主按钮。

### 5.8 表单与输入框
5.8.1 输入框高度 32px、内边距 12px、边框 1px `gray-300`、圆角 4px，Focus 边框主色 + 2px 外发光，Error 边框错误色 + 下方错误文本。

5.8.2 禁止用占位符代替标签，禁止输入框加阴影。

5.8.3 表单必须提供标签、校验反馈与键盘可达，错误时聚焦第一个错误字段。

### 5.9 表格
5.9.1 表格行高 32/40/48px，表头加粗、背景 `gray-100`，文本左对齐、数字右对齐，排序表头可点击带箭头指示。

5.9.2 分页可选 20/50/100，空状态 = 图标 + 文案 + 操作，加载用骨架屏。

5.9.3 禁止斑马纹，禁止悬停变色（除非整行可点）。

### 5.10 弹窗与消息提示
5.10.1 对话框宽度 400/600/800px，最大高度 80vh，圆角 8px，阴影 `shadow-lg`，遮罩 `rgba(0,0,0,0.45)`，支持遮罩/Esc/关闭按钮关闭，打开聚焦第一个可交互元素。

5.10.2 禁止对话框套对话框、禁止对话框全屏。

5.10.3 消息提示四类：Toast 顶部居中 3s 自动关、Notification 右上角 5s、Banner 页面顶部手动、Alert 内容区手动。

5.10.4 错误提示必须说明原因、提供解决路径并保留错误码，不暴露技术栈细节。

### 5.11 图标与动效
5.11.1 图标只用 Lucide 一个库，尺寸 16/20/24/32px，描边 1.5px 或 2px 全局统一，颜色继承文本色，图标与文字间距 8px。

5.11.2 禁止混用线性+填充图标、禁止混用多套图标库、禁止 emoji 当图标。

5.11.3 动效只为表达状态变化、时长 ≤ 300ms、必须尊重 `prefers-reduced-motion`。

5.11.4 只动画 `transform` 和 `opacity`（GPU 合成层），禁止动画 `width/height/top/left/box-shadow/filter: blur`。

5.11.5 进度条用 `transform: scaleX`（禁 `width`），骨架屏只动画 `opacity`（禁 `background-color`）。

5.11.6 禁止弹跳/回弹、旋转超 360°、缩放超 1.1、闪烁、视差滚动、自动播放、装饰性粒子等模板化 AI 感特效。

### 5.12 响应式与窗口
5.12.1 桌面优先工具，最小窗口 1024 × 720，推荐窗口 1440 × 900，内容区最大宽度 1600px。

5.12.2 顶栏 56px、底栏 32px、侧边栏 240px，作为固定布局常量使用。

## 6 数据库与 SQL 规范

### 6.1 命名
6.1.1 表名、字段名统一小写蛇形 `snake_case`，语义清晰，Java 字段用 camelCase 并显式映射。

6.1.2 主键 `id`，时间字段 `created_at / started_at / finished_at / update_time` 等统一时间类型。

### 6.2 双方言兼容（PG/MySQL）
6.2.1 所有 SQL 必须同时兼容 MySQL 8.0 与 PostgreSQL 16，通过 `DatabaseDialect` / `DialectRegistry` 按 `DatabaseMetaData.getDatabaseProductName()` 匹配方言。

6.2.2 标识符引用：MySQL 反引号、PG 双引号；自增：MySQL `AUTO_INCREMENT`、PG `SERIAL/GENERATED`；布尔：MySQL `TINYINT(1)`、PG `BOOLEAN`。

6.2.3 分页：MySQL `LIMIT offset,size`、PG `LIMIT size OFFSET offset`。

6.2.4 唯一写冲突统一封装：MySQL `INSERT IGNORE`、PG `INSERT ... ON CONFLICT DO NOTHING`。

6.2.5 不支持的数据源抛错误码 `110002`，禁止静默按单方言硬编码。

### 6.3 迁移（Flyway）
6.3.1 迁移脚本统一命名 `V{n}__desc.sql`（如 `V3__unify_task.sql`），按 `classpath:db/migration/mysql` 与 `classpath:db/migration/postgresql` 分目录。

6.3.2 Flyway 只迁移元数据库，目标库绝不跑 Flyway；关闭自动 Flyway（`spring.flyway.enabled=false`），自定义 `FlywayConfig` 仅注入 `metaDataSource`。

6.3.3 已发布迁移文件禁止修改、删除或重命名，变更一律新增迁移；`clean-disabled=true`、`baseline-on-migrate=true`。

6.3.4 生产环境禁止 `flyway clean`，回滚依赖数据库备份恢复，不依赖 Flyway。

## 7 文档与协作规范

### 7.1 文档结构
7.1.1 文档结构与导航遵循 `docs/README.md` 的索引，新增文档须登记到导航。

7.1.2 文档更新必须同步维护 `CHANGELOG.md`，记录变更内容、日期与影响范围。

7.1.3 错误码变更须同时更新 `ErrorCode` 枚举、`docs/api-spec.md` 第 4 章码表与前端 `ERROR_CODE_TEXT`，并由 `ErrorCodeContractTest` 校验。

### 7.2 协作
7.2.1 Git 分支：`main`（生产，禁直接提交）、`dev`（集成）、`feature/xxx`、`hotfix/xxx`、`release/vx.x.x`。

7.2.2 Commit 语义化：`feat / fix / refactor / perf / docs / security / style / test`。

7.2.3 合并前必须过代码评审五必查：魔法值、循环内查询、敏感数据明文、参数校验、异常吞掉。

7.2.4 提交忽略 IDE 配置、依赖缓存、日志、环境配置、密钥文件、编译产物，不提交敏感文件。

## 关联文档

- [README.md](README.md)（文档导航）
- [srs.md](srs.md)（需求规格说明）
- [architecture.md](architecture.md)（总体设计）
- [database-design.md](database-design.md)（数据库设计）
- [api-spec.md](api-spec.md)（接口规范与错误码）
- [testing.md](testing.md)（测试说明）
- [CHANGELOG.md](CHANGELOG.md)（变更记录）