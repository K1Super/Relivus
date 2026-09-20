# Relivus

数据库测试数据生成与脱敏系统 —— 面向 MySQL 与 PostgreSQL 的可视化测试数据工厂。

[![CI](https://github.com/K1Super/Relivus/actions/workflows/ci.yml/badge.svg)](https://github.com/K1Super/Relivus/actions/workflows/ci.yml)

## 核心能力

| 能力 | 说明 |
|---|---|
| 连接管理 | 支持 MySQL / PostgreSQL 元数据库，连接密码经 AES-256-GCM 加密存储，列表接口不回显明文 |
| 结构解析 | 外键拓扑排序、检查约束感知、方言差异适配 |
| 数据生成 | 本地 Faker 生成 + AI 增强（兼容 OpenAI / DeepSeek / GLM 等主流 API），AI 失败自动降级不中断任务 |
| 敏感数据脱敏 | 多算法可选、跨表关联一致性保障、保留原始语义 |
| 任务与可视化 | SSE 实时进度、生成数据在线回看（无需连接数据库即可校验） |
| 安全底座 | 六位三段式错误码体系、统一 Token 鉴权、审计日志 |

## 技术栈

- **后端**：Java 17 · Spring Boot 3.4 · Spring JDBC · Flyway · Testcontainers · Maven
- **前端**：React 18 · TypeScript · Vite · Ant Design · TanStack Query · Vitest

## 目录结构

```
Relivus/
├── backend/          # Spring Boot 后端服务（含 Flyway 迁移与单元/集成测试）
├── frontend/         # React 前端应用
├── docs/             # 项目文档（需求、设计、接口规范等）
├── deploy/           # 生产部署配置（systemd / Nginx / 环境变量模板）
└── scripts/          # 部署运维脚本
```

## 快速开始

环境准备与启动步骤见 [docs/getting-started.md](docs/getting-started.md)。

## 文档

完整文档导航见 [docs/README.md](docs/README.md)：

- 需求规格：[docs/srs.md](docs/srs.md)
- 总体设计：[docs/architecture.md](docs/architecture.md)
- 数据库设计：[docs/database-design.md](docs/database-design.md)
- 接口规范：[docs/api-spec.md](docs/api-spec.md)
- 编码规范：[docs/coding-standards.md](docs/coding-standards.md)
- 测试说明：[docs/testing.md](docs/testing.md)
- 部署指南：[docs/deployment-guide.md](docs/deployment-guide.md)
- 变更记录：[docs/CHANGELOG.md](docs/CHANGELOG.md)

## 许可证

本项目采用 [MIT License](LICENSE) 开源。