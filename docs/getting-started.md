# Relivus 快速开始（Getting Started）

| 文档版本 | v1.0 |
| 更新日期 | 2026-09-18 |
| 状态 | 正式 |

本文件带你从零启动 Relivus（测试数据生成与脱敏系统）并完成五分钟快速验证。生产环境部署见 [deployment-guide.md](deployment-guide.md)，接口见 [api-spec.md](api-spec.md)，问题排查见 [troubleshooting.md](troubleshooting.md)。

## 1 前置要求

| 依赖 | 版本要求 | 用途 |
|---|---|---|
| JDK | 17 | 后端运行与构建 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 20+ | 前端构建与开发 |
| PostgreSQL | 16（或 MySQL 8.0） | 元数据库（二选一） |
| （可选）Docker | 任意近期版本 | 仅后端集成测试的 Testcontainers，运行非必需 |

后端技术栈：Java 17 + Spring Boot 3.4 + Spring JDBC（无 JPA）+ Flyway，双方言（MySQL/PostgreSQL）元库。前端：React 18 + TypeScript + Vite + antd5。

## 2 获取与构建

```bash
# 获取代码后进入 backend 构建（跳过测试快速打包）
cd backend
mvn clean package -DskipTests
```

构建产物为 `backend/target/relivus.jar`（可执行 fat jar）。需要跑测试时用 `mvn test` / `mvn verify`，见 [testing.md](testing.md)。

## 3 环境变量配置

后端所有密钥与连接串经环境变量注入，`application.yml` 仅提供占位默认值与绑定结构。启动前必须设置以下变量，禁止将真实密钥硬编码进配置文件或版本库。

推荐做法：**本地开发使用根目录 `.env` 文件 + 启动脚本**，配置一次后每次启动自动生效，无需反复 export。

1. 复制模板并填写：

```bash
cp .env.example .env
```

`.env` 已被 `.gitignore` 忽略，不会进入版本库；模板 `.env.example` 提交到仓库且仅含占位符。

2. 运行一键启动脚本（自动读取 `.env`、校验必填项与密钥格式后启动）：

```bat
:: Windows
scripts\start-dev.bat
scripts\start-dev.bat -skipbuild
```

也可以不使用 `.env`，直接手动注入环境变量（见下）。两种方式等价，后者适合 CI 或容器场景。

| 环境变量 | 是否必填 | 说明 |
|---|---|---|
| RELIVUS_META_URL | 是 | 元数据库 JDBC 连接串，默认 `jdbc:mysql://127.0.0.1:3306/relivus_meta` |
| RELIVUS_META_USER | 是 | 元数据库用户名，默认 `relivus` |
| RELIVUS_META_PASSWORD | 是 | 元数据库密码（无默认，必填） |
| RELIVUS_TOKEN | 是 | API 鉴权 Bearer Token（无默认，必填） |
| RELIVUS_AES_KEY | 是 | 目标库密码/API Key 的 AES-GCM 加密密钥（Base64 编码的 32 字节） |
| RELIVUS_HMAC_KEY | 是 | 脱敏 HMAC 算法密钥（Base64 编码的 32 字节） |

密钥生成方式（Base64 编码 32 字节随机值）：

```bash
# Linux / macOS
openssl rand -base64 32

# 生成 Token（朴素的随即十六进制即可，建议足够长）
openssl rand -hex 32
```

Linux / macOS 导出示例：

```bash
export RELIVUS_META_URL='jdbc:mysql://127.0.0.1:3306/relivus_meta'
export RELIVUS_META_USER='relivus'
export RELIVUS_META_PASSWORD='change-me-strong'
export RELIVUS_TOKEN="$(openssl rand -hex 32)"
export RELIVUS_AES_KEY="$(openssl rand -base64 32)"
export RELIVUS_HMAC_KEY="$(openssl rand -base64 32)"
```

Windows（PowerShell）示例：

```powershell
$env:RELIVUS_META_URL='jdbc:postgresql://127.0.0.1:5432/relivus_meta'
$env:RELIVUS_META_USER='relivus'
$env:RELIVUS_META_PASSWORD='change-me-strong'
$env:RELIVUS_TOKEN='<生成的十六进制串>'
$env:RELIVUS_AES_KEY='<openssl rand -base64 32 的结果>'
$env:RELIVUS_HMAC_KEY='<openssl rand -base64 32 的结果>'
```

注意事项：

- `RELIVUS_AES_KEY` / `RELIVUS_HMAC_KEY` 必须是 Base64 解码后正好 32 字节；格式错误会导致启动失败或加解密失败（`500002`）。
- 更换 `RELIVUS_AES_KEY` 后，已存连接的密码密文无法解密，必须在 UI 重新保存连接密码（见 [troubleshooting.md](troubleshooting.md)）。

## 4 初始化元库

元数据库表结构由后端启动时 Flyway 自动迁移（只迁移元库，目标库绝不跑 Flyway）。首次启动前仅需创建数据库与用户，无需手动建表。可直接使用 `demo/` 目录脚本一并创建元库与演示目标库：

```bash
# PostgreSQL
psql -h 127.0.0.1 -U postgres -f demo/postgresql/init.sql

# MySQL
mysql -h 127.0.0.1 -u root -p < demo/mysql/init.sql
```

脚本会创建应用用户 `relivus`、元库 `relivus_meta`、演示目标库 `relivus_demo`（含 `users` / `orders` 表，覆盖 ENUM / CHECK / UNIQUE / 外键）。脚本内密码为占位值 `change-me-strong`，正式使用前必须改为强密码并同步到环境变量。若手动建库，MySQL 需指定 `utf8mb4`：

```sql
CREATE DATABASE relivus_meta DEFAULT CHARACTER SET utf8mb4;
```

## 5 启动后端

后端默认端口 `8080`，支持优雅停机。启动方式：

```bat
:: 方式一：一键脚本（推荐，自动读取根目录 .env）
scripts\start-dev.bat

:: 方式二：直接运行 fat jar（需已注入环境变量）
cd backend
java -jar target\relivus.jar

:: 方式三：Spring Boot Maven 插件（开发常用，需已设置环境变量）
mvn spring-boot:run
```

健康检查：

```bash
curl -sf http://127.0.0.1:8080/actuator/health
# 期望返回：{"status":"UP"}
```

首次启动会看到 Flyway 迁移日志；迁移完成后 `/actuator/health` 返回 UP。OpenAPI 文档位于 `http://127.0.0.1:8080/swagger-ui.html`。

## 6 启动前端

```bash
cd frontend
npm install     # 或 npm ci（有 lockfile 时）
npm run dev
```

Vite 开发服务器默认端口 `5173`，并将 `/api` 代理到后端 `http://localhost:8080`。访问：

```text
http://localhost:5173
```

首次进入后到设置页填写鉴权 Token（与 `RELIVUS_TOKEN` 一致），之后所有 API 请求自动携带 `Authorization: Bearer <Token>`。

生产构建：`npm run build`（先 `tsc --noEmit` 再做 `vite build`），产物输出到 `frontend/dist/`，由 Nginx 托管（见 [deployment-guide.md](deployment-guide.md)）。

## 7 五分钟快速验证

1. 启动元库，设置 6 项环境变量，执行 `demo/` 对应方言的 init.sql。
2. 启动后端，`curl http://127.0.0.1:8080/actuator/health` 返回 `UP`。
3. 启动前端，浏览器打开 `http://localhost:5173`，在设置页填入 Token。
4. 新建连接：选择 `postgresql`，主机 `127.0.0.1`、端口 `5432`、数据库 `relivus_demo`、用户名/密码 `relivus` / `change-me-strong`，点击测试连接成功。
5. Schema 页内省：查看 `users`、`orders` 表及其外键与 CHECK 约束。
6. 生成页配置 `users` 1000 行、`orders` 5000 行，先预览（每表 ≤5 行）再执行，观察 SSE 进度条推进至 SUCCESS。
7. 脱敏页对 `users.email` 选择 hmac 算法执行，验证同列同值结果一致。
8. 任务页确认任务状态为 SUCCESS，并可用 psql 校验行数与约束（SQL 见 [testing.md](testing.md) 第 6 节）。
9. （可选）AI 生成：设置页新建 AI 配置（OpenAI 兼容 baseUrl + apiKey + model）并激活、测试连通，随后在生成页把某列生成器选为 `ai`。

## 8 示例数据

`demo/` 下按方言提供初始化脚本，同时完成元库与演示目标库的创建：

| 文件 | 方言 | 执行方式 |
|---|---|---|
| demo/postgresql/init.sql | PostgreSQL | `psql -h 127.0.0.1 -U postgres -f demo/postgresql/init.sql` |
| demo/mysql/init.sql | MySQL | `mysql -h 127.0.0.1 -u root -p < demo/mysql/init.sql` |

演示目标库 `relivus_demo` 结构：

- `users`：`id`（自增主键）、`email`（NOT NULL + UNIQUE）、`name`、`age`（CHECK 18–70）、`gender`（ENUM）、`balance`、`created_at`。
- `orders`：`id`（自增主键）、`user_id`（外键 → users.id）、`amount`（CHECK > 0）、`status`（ENUM）、`created_at`。

目标库由用户在 UI 中配置连接，Relivus 不迁移目标库；仅元库 `relivus_meta` 由 Flyway 迁移。

## 关联文档

- [deployment-guide.md](deployment-guide.md) — 生产环境部署
- [api-spec.md](api-spec.md) — 接口与错误码契约
- [testing.md](testing.md) — 测试与数据校验
- [troubleshooting.md](troubleshooting.md) — 常见问题排查