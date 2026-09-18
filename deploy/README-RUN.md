# Relivus 运行手册（README-RUN）

单机部署（systemd + Nginx，无 Docker）。元库与业务目标库均支持 MySQL 8.0 / PostgreSQL 16。

## 1. 环境要求

- JDK 17
- Maven 3.9+
- Node 20+
- MySQL 8.0 或 PostgreSQL 16（元数据库）
- 目标库按需安装 MySQL 8.0 / PostgreSQL 16
- Nginx 1.27+
- 8GB 内存起步

## 2. 首次部署

1. 初始化元数据库与演示目标库（任选其一）：

   - PostgreSQL：`psql -h 127.0.0.1 -U postgres -f demo/postgresql/init.sql`
   - MySQL：`mysql -h 127.0.0.1 -u root -p < demo/mysql/init.sql`

   元库建库建用户；**元库表结构由应用启动时 Flyway 自动迁移**（只迁移元库，绝不迁移目标库）。
   目标库表结构由用户在 UI 中配置连接后自行建表（可参考 `demo/` 下脚本）。

2. 部署后端：

   ```bash
   cd backend
   mvn clean package -DskipTests
   sudo install -m 644 target/relivus.jar /opt/relivus/relivus.jar

   sudo mkdir -p /etc/relivus /opt/relivus
   sudo cp deploy/relivus.env.example /etc/relivus/relivus.env
   sudo chown -R relivus:relivus /etc/relivus /opt/relivus
   sudo chmod 600 /etc/relivus/relivus.env
   # 编辑 /etc/relivus/relivus.env 填写 RELIVUS_META_URL / USER / PASSWORD / AES / HMAC / TOKEN

   sudo cp deploy/relivus-backend.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable relivus-backend
   ```

3. 部署前端：

   ```bash
   cd frontend
   npm ci
   npm run build
   sudo mkdir -p /var/www/relivus
   sudo cp -r dist/* /var/www/relivus/
   ```

4. 配置 Nginx：

   ```bash
   sudo cp deploy/nginx-relivus.conf /etc/nginx/conf.d/relivus.conf
   sudo nginx -t && sudo systemctl reload nginx
   ```

## 3. 启动

```bash
./scripts/start.sh
```

首次启动时 Flyway 自动迁移元数据库（`df_connection`、`df_task`、`df_task_log`、`df_mask_mapping`、`df_audit_log`），
可用 `curl -sf http://127.0.0.1:8080/actuator/health` 确认健康（返回 UP）。

## 4. 访问

- 浏览器打开 `http://localhost`（前端静态页）。
- 所有 `/api/**` 请求必须在请求头携带 `Authorization: Bearer ${RELIVUS_TOKEN}`。
- 前端"设置"页可输入并保存 Token 到浏览器 localStorage。

## 5. 验证

按顺序在 UI 完成一次冒烟：

1. 设置页保存 Token。
2. 连接管理：创建元库同实例的目标库连接并"测试连接"。
3. Schema 扫描：确认能读到 users / orders 的表、列、索引、外键、ENUM、CHECK。
4. 生成：配置 1000 行写入演示目标库，任务页观察 SSE 实时进度，完成后在目标库核对行数。
5. 脱敏：对同一连接执行脱敏，再执行 JOIN 验证（验证通过率 100%）。

## 6. 备份

```bash
./scripts/backup.sh
```

备份 `df_mask_mapping`、`df_task`、`df_task_log`、`df_audit_log` 四张元表到 `backups/`，文件名含时间戳。
（脚本按 `RELIVUS_META_URL` 自动识别元库为 PostgreSQL 或 MySQL。）

## 7. 恢复

```bash
./scripts/restore.sh backups/relivus_meta_xxx_TIMESTAMP.sql
```

## 8. 重跑任务

- 任务失败 / 取消后回到任务列表重新执行同一配置即可，无需处理旧数据行数，
  生成前勾选"截断目标表（truncateBefore）"可清空表中已有数据。

## 9. 回滚

1. `systemctl stop relivus-backend`。
2. 用第 7 步恢复数据库备份。
3. 重新 `./scripts/start.sh`。

> Flyway 不允许回退迁移；升级即回滚场景一律依赖数据库备份恢复，不要手工删除迁移记录。

## 10. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 健康检查失败 | 元库连接信息错误 / 未启动 | 检查 `/etc/relivus/relivus.env`、数据库可达性与 Flyway 迁移日志 |
| 端口 8080 被占用 | 旧实例仍在运行 | `systemctl stop relivus-backend` 或处理占用进程 |
| API 返回 401 / code 9001 | 未携带 Token 或 Token 错误 | 检查请求头 `Authorization: Bearer <TOKEN>` |
| SSE 进度断线 | Nginx `proxy_buffering` 未关 / 超时过短 | 确认使用 `deploy/nginx-relivus.conf`（buffering off、read_timeout 30m） |
| 生成/脱敏冲突 | 目标表唯一键冲突、映射表被并发写 | 生成走查重兜底自动重试；脱敏映射表冲突时清理 `df_mask_mapping` 后重跑 |
| SWAGGER 界面 | 默认关闭 | 设置 `RELIVUS_SWAGGER_ENABLED=true` 后重启 |
| 目标库连接失败 | 连接配置或目标库未创建表 | 在连接管理中确认后重新测试连接 |

## 附录：目录约定

```text
/opt/relivus/relivus.jar        后端可执行包
/etc/relivus/relivus.env        生产环境变量（chmod 600）
/etc/systemd/system/relivus-backend.service
/var/www/relivus/               前端构建产物
/etc/nginx/conf.d/relivus.conf  Nginx 反向代理与 SSE 配置
backups/                        备份目录（由 scripts/backup.sh 创建）
```