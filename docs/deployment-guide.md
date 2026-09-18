# Relivus 部署指南（Deployment Guide）

| 文档版本 | v1.0 |
| 更新日期 | 2026-09-18 |
| 状态 | 正式 |

本文件描述 Relivus 的裸机生产部署方式（systemd + Nginx，全链路无 Docker）。快速体验开发环境见 [getting-started.md](getting-started.md)，问题排查见 [troubleshooting.md](troubleshooting.md)。

## 1 生产环境要求

| 依赖 | 要求 | 说明 |
|---|---|---|
| JDK | 17 | 后端运行 |
| Maven | 3.9+ | 构建（仅构建机需要） |
| Node.js | 20+ | 前端构建（仅构建机需要） |
| 数据库 | MySQL 8.0 或 PostgreSQL 16 | 元数据库（二选一）；目标库按需安装对应方言 |
| Nginx | 1.27+ | 前端静态托管与 `/api` 反代、SSE 转发 |
| 内存 | 8GB 起步 | 后端 JVM + 数据库 + Nginx |

架构要点：

- 后端进程监听 `127.0.0.1:8080`，由 Nginx 对外暴露 80。
- 元数据库表结构由 Flyway 自动迁移；目标库由用户在 UI 中配置，绝不跑 Flyway。
- 目标是单用户内部工具，不需要 RBAC、登录、SSO；鉴权仅靠 `Authorization: Bearer ${RELIVUS_TOKEN}`。

## 2 构建产物

### 2.1 后端

```bash
cd backend
mvn clean package -DskipTests
# 产物：backend/target/relivus.jar
```

`relivus.jar` 为可执行 fat jar（内嵌 Tomcat）。

### 2.2 前端

```bash
cd frontend
npm ci
npm run build
# 产物：frontend/dist/（纯静态文件）
```

前端静态产物由 Nginx 托管（`/var/www/relivus/`），并通过 Nginx 将 `/api/` 反代到后端，二者集成于同一域名，无跨域问题。

## 3 服务器部署

### 3.1 目录规划

```text
/opt/relivus/relivus.jar        # 后端 fat jar
/etc/relivus/relivus.env        # 环境变量（权限 600，属主 relivus）
/etc/systemd/system/relivus-backend.service
/var/www/relivus/               # 前端静态产物
/etc/nginx/conf.d/relivus.conf  # Nginx 站点配置
```

### 3.2 systemd 服务单元

创建 `/etc/systemd/system/relivus-backend.service`：

```ini
[Unit]
Description=Relivus Backend
After=network.target mysql.service postgresql.service

[Service]
User=relivus
EnvironmentFile=/etc/relivus/relivus.env
ExecStart=/usr/bin/java -jar /opt/relivus/relivus.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

启用与启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now relivus-backend
sudo systemctl status relivus-backend
curl -sf http://127.0.0.1:8080/actuator/health
```

## 4 Nginx 反代配置

`/etc/nginx/conf.d/relivus.conf`：

```nginx
server {
    listen 80;
    server_name relivus.local;
    root /var/www/relivus;
    index index.html;

    # 前端 SPA 回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 普通 API 反代
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header Authorization $http_authorization;
    }

    # SSE 进度端点：必须关闭缓冲，否则事件被积压不实时推送
    location ~ ^/api/tasks/.*/progress$ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Connection '';
        proxy_set_header Authorization $http_authorization;
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding off;
        proxy_read_timeout 30m;
    }
}
```

SSE 要点：

- `proxy_buffering off`：关闭响应缓冲，挂起长连接时事件即时下发。
- `proxy_cache off` + `chunked_transfer_encoding off`：禁用缓存与分块编码，保证事件流原样透传。
- `proxy_read_timeout 30m`：与后端 SSE 30 分钟超时对齐，避免长连接被过早掐断。
- `location ~ ^/api/tasks/.*/progress$` 必须置于 `/api/` 之前生效（Nginx 前缀与正则优先级，此处正则更精确优先）。

应用配置后：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

## 5 环境变量与密钥管理

`/etc/relivus/relivus.env`（`chmod 600`，属主 `relivus`）：

```bash
RELIVUS_META_URL=jdbc:mysql://127.0.0.1:3306/relivus_meta
RELIVUS_META_USER=relivus
RELIVUS_META_PASSWORD=强密码
RELIVUS_AES_KEY=Base64的32字节密钥
RELIVUS_HMAC_KEY=Base64的32字节密钥
RELIVUS_TOKEN=固定Token
```

密钥生成（Base64 编码 32 字节）：

```bash
openssl rand -base64 32   # AES key 与 HMAC key 各一次
openssl rand -hex 32      # Token
```

密钥管理约束：

- 全部密钥与连接串经环境变量注入，禁止硬编码进 `application.yml` 或版本库；`.env` 文件不进版本库。
- `RELIVUS_AES_KEY` 用于目标库密码、AI apiKey 的 AES-GCM 加密存储；`RELIVUS_HMAC_KEY` 用于脱敏 HMAC 算法。
- 生产密钥轮换：先在数据库侧/配置侧准备新值 → 停服 → 更新 `/etc/relivus/relivus.env` → 起服。**换 AES key 后，已存连接的密码密文与已存 AI 配置的 apiKey 密文将无法解密**（报 `500002` 或连接失败），必须重新在 UI 保存这些连接的密码、重新填写 AI 配置的 apiKey。
- 换 HMAC key 后，历史脱敏结果与旧映射不匹配，如需跨版本一致性请谨慎轮换；正式轮换需同步更新 `relivus.masking.key-version`。

## 6 升级流程

标准升级顺序：备份 → 停服 → 替换 jar → 启动 → 验证。

```bash
# 1. 备份元库关键表（示例为 MySQL，PG 用 pg_dump）
mysqldump -h127.0.0.1 -uroot -p"$RELIVUS_META_PASSWORD" relivus_meta \
  df_mask_mapping df_task df_task_log df_audit_log > backups/meta_$(date +%Y%m%d-%H%M%S).sql

# 2. 停服
sudo systemctl stop relivus-backend

# 3. 替换 jar（确保进程已停止，避免文件占用）
cd backend && mvn clean package -DskipTests
sudo install -m 644 target/relivus.jar /opt/relivus/relivus.jar

# 4. 启动并验证
sudo systemctl start relivus-backend
until curl -sf http://127.0.0.1:8080/actuator/health > /dev/null; do sleep 2; done
echo "OK"
```

Windows 环境注意事项：

- Windows 上正在运行的 jar 文件被进程锁定，无法直接覆盖，否则报文件占用错误。
- 必须先停止后端进程（Ctrl+C 或结束对应 java 进程）再替换 `relivus.jar`，否则复制/覆盖失败。
- Flyway 不允许回退，升级依赖 Flyway 前滚版本叠加；破坏性回退依赖数据库备份恢复。

## 7 健康检查与监控

- 健康检查：`GET /actuator/health`（Actuator 暴露 `health,info` 端点，health 含探针）。

```bash
curl -s http://127.0.0.1:8080/actuator/health
# {"status":"UP"}
```

- 日志：后端日志输出到标准输出（prod profile 为单行 `|` 分隔字段 `ts|level|traceId|logger|msg`），由 systemd 收集到 journal：

```bash
sudo journalctl -u relivus-backend -f          # 实时跟踪
sudo journalctl -u relivus-backend -n 200      # 最近 200 行
```

- 任务表监控建议：周期性查询元库 `df_task`，关注长期处于 `RUNNING` 的任务与失败任务：

```sql
SELECT id, task_type, status, progress, processed_rows, total_rows, started_at
FROM df_task
WHERE status = 'RUNNING' AND started_at < NOW() - INTERVAL '30 minute';
```

- 服务端故障码（`5xxxxx`）统一记 ERROR 日志 + 完整堆栈，可结合响应中的 `traceId` 在日志中检索定位。

## 8 回滚

```bash
# 1. 停服
sudo systemctl stop relivus-backend

# 2. 恢复元库备份
mysql -h127.0.0.1 -uroot -p"$RELIVUS_META_PASSWORD" relivus_meta < backups/meta_xxx.sql
# PostgreSQL：psql -h127.0.0.1 -U relivus relivus_meta < backups/meta_xxx.sql

# 3. 恢复旧版本 jar（或重新构建）
sudo install -m 644 /path/to/relivus-old.jar /opt/relivus/relivus.jar

# 4. 启动验证
sudo systemctl start relivus-backend
curl -sf http://127.0.0.1:8080/actuator/health
```

回滚原则：

- Flyway 不提供自动回退，破坏性变更的回滚依赖数据库备份恢复。
- 备份覆盖 `df_mask_mapping`、`df_task`、`df_task_log`、`df_audit_log` 等关键表。
- 前端静态产物回滚重新部署旧版本 `dist/` 即可。

## 关联文档

- [getting-started.md](getting-started.md) — 快速开始
- [troubleshooting.md](troubleshooting.md) — 部署与运行问题排查
- [api-spec.md](api-spec.md) — 接口契约
- [testing.md](testing.md) — 测试与验收