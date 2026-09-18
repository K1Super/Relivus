# Relivus 常见问题排查（Troubleshooting）

| 文档版本 | v1.0 |
| 更新日期 | 2026-09-18 |
| 状态 | 正式 |

本文件按「现象 → 排查 → 解决」组织常见问题，覆盖启动、连接、生成、SSE、AI、Flyway、Windows 部署、数据校验与日志。接口与错误码见 [api-spec.md](api-spec.md)，部署见 [deployment-guide.md](deployment-guide.md)。

## 1 后端启动失败

### 1.1 端口 8080 被占用

现象：启动日志报 `Port 8080 was already in use` 或 `BindException`。

排查：

```bash
# Linux
ss -lntp | grep 8080

# Windows（PowerShell）
netstat -ano | findstr :8080
```

解决：结束占用进程，或改用其他端口（`--server.port=8081`）并同步调整前端/Nginx 反代目标。

### 1.2 密钥环境变量缺失或格式错误

现象：应用启动即报 `RELIVUS_AES_KEY` / `RELIVUS_HMAC_KEY` / `RELIVUS_TOKEN` 为空，或运行时报加解密失败 `500002`。

排查：

```bash
# 确认变量已注入
echo "$RELIVUS_AES_KEY" "$RELIVUS_TOKEN"
```

解决：

- `RELIVUS_AES_KEY`、`RELIVUS_HMAC_KEY` 必须是 Base64 解码后恰好 32 字节，生成方式：`openssl rand -base64 32`。
- `RELIVUS_TOKEN`、`RELIVUS_META_PASSWORD` 无默认值，未设置会启动失败。
- 通过 systemd 运行时，变量定义在 `/etc/relivus/relivus.env`，修改后需 `systemctl daemon-reload` 并重启服务。

### 1.3 元库不可达

现象：启动日志反复报数据库连接失败，Flyway 或 Hikari 初始化异常。

排查：

```bash
# 手动验证元库连通（按方言二选一）
psql -h 127.0.0.1 -U relivus -d relivus_meta -c 'SELECT 1;'
mysql -h 127.0.0.1 -u relivus -p -e 'SELECT 1;' relivus_meta
```

解决：确认 `RELIVUS_META_URL` 的方言与端口正确、数据库存在、用户有权限、防火墙放行、`RELIVUS_META_PASSWORD` 正确。

## 2 健康检查不通过

现象：`/actuator/health` 返回 `DOWN` 或非 200。

排查：

```bash
curl -s http://127.0.0.1:8080/actuator/health
curl -s http://127.0.0.1:8080/actuator/health | python -m json.tool
```

解决思路：

- 元库不可达（见 1.3）。
- 后端未完全启动：查看启动日志与 Flyway 迁移输出。
- 注意 `/actuator/**` 无需 Token，但 8080 端口需本地可达；经 Nginx 访问时确认 `/api/` 反代正常。

## 3 连接测试失败

现象：UI 连接测试报「目标库连接失败」，接口返回 `110001`。

排查：

```bash
# 网络可达性（按目标库端口）
nc -zv <host> <5432或3306>     # 或 PowerShell: Test-NetConnection <host> -Port 5432

# 凭据与库
psql "postgresql://<user>:<pass>@<host>:<port>/<db>" -c 'SELECT 1;'
mysql -h <host> -P <port> -u <user> -p -e 'SELECT 1;' <db>
```

可能原因与解决：

- 驱动：后端已内嵌 `mysql-connector-j` 与 `postgresql` 驱动，无需额外安装；若报驱动异常多为 `dbType` 拼写错误（仅 `mysql` / `postgresql`，否则报 `110002` 不支持的数据库）。
- 网络/防火墙：确认目标库监听地址与防火墙放行。
- 凭据：确认用户名密码，注意连接密码存储为 AES-GCM 密文。

**AES key 变更导致旧连接解密失败**：若运行报错指向 `500002`（密钥加解密失败）或测试连接失败，且近期更换过 `RELIVUS_AES_KEY`，原因是已存连接密码密文用旧 key 加密、新 key 无法解密。解决：在 UI 重新编辑每个连接并重新保存密码（此时会用新 key 重新加密）。同理需重新填写 AI 配置的 apiKey。

## 4 生成任务失败或缓慢

### 4.1 行数超上限

现象：请求返回 `400 + 100002`，fieldErrors 提示 `rowCount must be <= 500000`。

解决：单表 `rowCount` 上限为 500000，拆分批次或减小行数。

### 4.2 批大小不当导致缓慢

现象：大批量生成缓慢或内存占用偏高。

解决：调整 `batchSize`（默认 1000）；线程池核心 4 / 最大 8、队列 100，任务量大时队列满会返回 `150004`（任务队列已满），稍后重试。

### 4.3 唯一约束冲突超限

现象：任务失败返回 `130002`（唯一约束冲突超限）或任务日志出现大量重试。

排查：目标表存在低基数唯一列（如 ENUM/布尔），可生成的唯一值不足。

解决：

- 为唯一列显式指定足够基数的生成器（如 `random_int` 大范围、`faker` uuid、`regex`）。
- NOT NULL 唯一列必须保证值基数 ≥ 行数；组合唯一约束检查列组基数。

### 4.4 循环依赖 FK 为 NOT NULL

现象：返回 `130003`（循环依赖的外键列不允许为空）。

解决：循环依赖中的外键列必须可空（内省时校验）；改为可空 FK，或先建立初始行打破环。

## 5 SSE 进度不推送

### 5.1 Nginx 缓冲未关闭

现象：任务在跑但进度条不动、事件堆积，任务结束后一次性到达。

排查：确认 Nginx 配置了 SSE 专用 location 且 `proxy_buffering off`：

```nginx
location ~ ^/api/tasks/.*/progress$ {
    proxy_http_version 1.1;
    proxy_set_header Connection '';
    proxy_buffering off;
    proxy_cache off;
    chunked_transfer_encoding off;
    proxy_read_timeout 30m;
}
```

解决：补全上述配置后 `nginx -t && systemctl reload nginx`。注意该 location 需位于 `/api/` 之前（正则更精确优先）。

### 5.2 SSE 连接数超限

现象：订阅进度返回 `429 + 150003`（实时进度连接数超限）。

排查：全局 SSE 并发连接上限为 20。

解决：关闭多余进度页签/客户端，稍后重试；确认无异常连接泄漏（断线自动清理 onCompletion/onTimeout/onError）。

## 6 AI 生成问题

### 6.1 返回 502 AI_UPSTREAM_FAILED（`160002`）

现象：AI 测试或生成返回 `160002`。

排查与解决：

- baseUrl 是否为 OpenAI 兼容接口且可访问（`/v1` 路径是否补齐）。
- apiKey 是否有效、是否还有额度。
- 网络是否可达上游（代理/防火墙/域名解析）。
- 上游限流（429）或无额度：稍后重试或换配置。

### 6.2 返回 404 AI_CONFIG_NOT_FOUND（`160001`）

现象：生成时报 `160001`。

解决：未配置或未激活 AI。到设置页新建 AI 配置并激活；`activate` 保证单活跃，生成时实时解析激活项。若删除的是激活配置，需重新激活新配置。

### 6.3 任务日志出现「AI 生成降级为本地假数据」WARN

现象：任务不失败，但任务日志/SSE `log` 事件出现 WARN：`AI 生成降级为本地假数据：表 X 列 Y（原因）`。

说明：这是设计内的降级行为——AI 上游运行时失败（网络、限流、空结果等）时，该列降级为本地假数据（按列名启发式 + 类型默认），降级后不再回试 AI，任务照常完成。仅「AI 配置缺失」（`160001`）会整体失败，降级不触发于缺失场景。

排查：根据 WARN 中的「原因」处理——占位 key（如示例 `sk-...` 未替换）、网络不可达、上游限流等；确认后可重新执行任务。

## 7 Flyway 迁移失败 / 校验冲突

### 7.1 校验冲突（checksum mismatch）

现象：启动报 `FlywayValidateException` / migration checksum mismatch。

说明：迁移脚本已发布到环境后不允许修改内容；升级应新增迁移版本。

解决：确认未误改 `db/migration/{mysql,postgresql}` 下已有脚本；若为误改，还原脚本内容后重启。

### 7.2 其他

- Flyway 只迁移元库（`FlywayConfig` 只注入 `metaDataSource`），目标库绝不跑 Flyway；确认 `RELIVUS_META_URL` 指向元库。
- 回滚不依赖 Flyway，依赖数据库备份恢复（见 [deployment-guide.md](deployment-guide.md) 第 8 节）。

## 8 Windows 覆盖 jar 失败（文件锁）

现象：Windows 上 `mvn package` 或复制 jar 报文件占用/拒绝访问。

原因：正在运行的 `relivus.jar` 被 java 进程锁定。

解决：

```powershell
# 找到并结束 java 进程（先确认是 Relivus 后端进程）
Get-Process java | Select-Object Id, Path
Stop-Process -Id <pid>
```

必须先停进程再覆盖 jar；若用 IDE 运行需在 IDE 停止，或改用 `mvn spring-boot:run`（非 packaged jar，无单文件锁）。

## 9 数据质量校验

生成/脱敏完成后用 SQL 校验约束满足情况（PostgreSQL 演示库）：

```sql
-- 年龄区间（users.age ∈ 18..70）
SELECT COUNT(*) AS out_of_range FROM users WHERE age < 18 OR age > 70;   -- 期望 0

-- 邮箱格式
SELECT COUNT(*) AS bad_email
FROM users
WHERE email !~ '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$';      -- 期望 0

-- 唯一性
SELECT email, COUNT(*) FROM users GROUP BY email HAVING COUNT(*) > 1;    -- 期望空

-- 外键完整性
SELECT COUNT(*) AS orphan_orders
FROM orders o LEFT JOIN users u ON o.user_id = u.id
WHERE u.id IS NULL;                                                       -- 期望 0

-- 行数
SELECT (SELECT COUNT(*) FROM users) AS users_rows,
       (SELECT COUNT(*) FROM orders) AS orders_rows;
```

MySQL 等价：邮箱 `email NOT REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'`。

## 10 日志查看位置与结构化字段

后端不使用独立日志文件，全部输出到标准输出，由运行方式决定去向：

- systemd（prod）：`sudo journalctl -u relivus-backend -f`。
- 控制台/IDE：直接查看终端输出。

日志格式：

- prod profile（`--spring.profiles.active=prod`）：单行 `|` 分隔结构化字段，便于采集解析：

```text
2026-09-18T10:05:00.123+08:00|INFO|traceId值|com.relivus.task.TaskService|任务已创建
```

字段顺序：`时间|级别|traceId|logger|消息`。无 ANSI 转义。

- 非 prod profile（默认）：`%d{...} %-5level [%thread] [traceId=...] %logger - %msg`，且 `com.relivus` 日志级别为 DEBUG。

排查技巧：

- 所有响应（含成功）携带 `traceId`（源自 `X-Trace-Id` 请求头或服务端生成），错误排查时以 `traceId` 在日志中检索对应请求链路。
- `5xxxxx` 服务端故障码记 ERROR 日志 + 完整堆栈；`1xxxxx` 可预期业务失败记 WARN。
- 敏感信息防泄漏：Hikari 与 MySQL 驱动日志级别收紧为 WARN，SQL 参数摘要级别受控，密钥/密码不进日志；AI 配置仅 `name`/`model` 记入 DEBUG，apiKey 密封文出参均不回传。

## 关联文档

- [deployment-guide.md](deployment-guide.md) — 部署与回滚
- [api-spec.md](api-spec.md) — 错误码与响应契约
- [getting-started.md](getting-started.md) — 快速开始
- [testing.md](testing.md) — 测试与数据校验