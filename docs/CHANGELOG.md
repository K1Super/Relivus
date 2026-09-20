# 变更记录

| 文档版本 | v1.0 |
|---|---|
| 更新日期 | 2026-09-18 |
| 状态 | 正式 |

## [Unreleased]

- 2026-09-20 工程化整改：Service 层按编码规范接口化（`IConnectionService` / `IAiConfigService` / `ITaskService` / `ITaskDataService` 接口 + 对应 `Impl` 实现，纯重构无逻辑变更）；修复 CI 对启动脚本的检查项（`start-dev.ps1`），前端流水线新增 `npm run build`（含 `tsc --noEmit`）类型检查与构建门禁；修复 README 与文档导航中的失效链接（`srs.md` / `architecture.md`），CI 徽章替换为实际仓库地址；移除不存在的 `demo/` 示例库目录引用，目标库统一表述为用户自行准备。
- 2026-09-18 文档体系重组：docs 由 19 篇旧文档整合为 11 篇标准文档（本 CHANGELOG 记录历史版本）。

## [2.3.0] - 2026-09-18

### Changed

- 生成器语料整改：新增 `ChinesePersonData` 现代中文语料（50 常见姓氏 + 50 常用名 + 8 主流邮箱域名），`FakerGenerator` 与 `AiGenerator` 降级路径统一复用；姓名输出 2~4 字现代常用中文（剔除生僻字），邮箱输出 RFC 合规纯 ASCII 地址（拼音 @ 常见域名），不再出现汉字邮箱与古风复姓。
- `TimestampGenerator` 表内单调递增且永不未来：按行号在默认 5 年窗口内线性推进（含 1/8 步长抖动），并向上截断至当前时间。
- `BatchInserter`：PostgreSQL 方言下自增主键回填改为逐行 INSERT 取 key，规避批量 `INSERT ... RETURNING` 的 keys 返回顺序不保证导致的主键回填错位，确保 `created_at` 与 `id` 严格单调对应。
- 姓名与性别联动：`ChinesePersonData` 名库按男/女分库（男 29 例、女 21 例），引擎整行生成后校验 name 性别倾向与 gender 一致，不一致按 gender 重生成，保证「男是男名、女是女名」。
- 文档：`database-design.md` / `testing.md` 同步数据质量整改内容（枚举、精度、约束、质量校验 SQL）。

### Fixed

- 生成数据时间顺序错乱与未来时间（整改 P0）。
- 邮箱含中文不符合 RFC、姓名含生僻字（整改 P1/P2）。
- gender 枚举值混入字母导致与数字 0 混淆（整改 P2）。

### Tests

- `ValueGeneratorUnitTest` 新增 3 项：姓名形态（2~4 字且无空白/生僻字）、邮箱 RFC 合规（纯 ASCII + 正则 + 长度）、时间戳（表内单调递增 + 永不未来）。

## [2.2.0] - 2026-09-18

### Added

- 生成数据可视化回看：数据生成任务成功后，可在页面内直接查看本次生成的行数据（按表切换、服务端分页、列类型渲染），无需再打开数据库客户端。新增 `GET /api/tasks/{id}/tables`（表清单）与 `GET /api/tasks/{id}/data`（分页数据）端点。
- 基线采集：任务执行前按各表数值主键采集 `MAX(pk)` 并持久化于 `df_task.data_baseline_json`（Flyway V3，双方言）；truncate 模式基线归零，无主键/非数值主键表回退全表展示并在前端提示。
- 错误码新增 `150005 TASK_DATA_UNAVAILABLE`（任务未成功完成，生成数据不可回看）。

## [2.1.0] - 2026-09-18

### Added

- AI 测试数据生成能力：基于 OpenAI 兼容协议，支持 DeepSeek、GLM 等主流 API；设置页新增「AI 模型配置」标准配置面板；列级 ai 生成器支持自定义 prompt；上游失败时自动降级本地假数据，并在任务日志记录 WARN。
- 错误码新增 60 段 AI 模块：AI_CONFIG_NOT_FOUND 160001、AI_UPSTREAM_FAILED 160002、AI_SERVICE_ERROR 560001。
- 元库新增 df_ai_config 表（Flyway V2，双方言）。

### Fixed

- age 生成区间收敛至 18~70。
- 姓名、邮箱恢复语义化中文数据。
- rowCount 增加上限 500000 校验。
- 正则参数增加长度上限，并改用带错误码的校验异常。

## [2.0.0] - 2026-09

### Changed

- 错误码体系 v2：采用六位三段式 A-BB-CCC（责任方-模块-序号），建立 HTTP 状态与日志级别映射，ErrorCode 枚举作为唯一来源，契约测试保障，前端 ERROR_CODE_TEXT 同步映射。

## [1.0.0] - 初始版本

### Added

- 数据库连接管理（密码 AES-GCM 加密存储）。
- Schema 内省。
- 测试数据生成引擎（外键 DAG、ENUM、CHECK 感知，批量插入，唯一约束保护，SSE 进度）。
- 数据脱敏（哈希、替换、正则等算法）。
- 任务管理。
- Bearer Token 鉴权。
- 审计日志。
- 统一响应 ApiResponse。