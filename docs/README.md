# Relivus 文档导航

Relivus：数据库测试数据生成与脱敏系统。

| 文档版本 | v1.0 |
|---|---|
| 更新日期 | 2026-09-18 |
| 状态 | 正式 |

## 文档目录

| 文档 | 用途 | 建议读者 |
|---|---|---|
| [SRS.md](SRS.md) | 需求规格说明，描述系统功能与非功能需求 | 产品、测试、开发 |
| [high-level-design.md](high-level-design.md) | 总体设计，描述系统架构与模块划分 | 架构师、后端开发 |
| [database-design.md](database-design.md) | 数据库设计，描述数据模型与元库表结构 | 后端开发、DBA |
| [api-spec.md](api-spec.md) | 接口规范，定义 REST API 契约 | 前后端开发 |
| [getting-started.md](getting-started.md) | 快速开始，说明环境准备与启动步骤 | 新人、所有开发者 |
| [coding-standards.md](coding-standards.md) | 编码规范，约定代码风格与质量要求 | 开发 |
| [testing.md](testing.md) | 测试说明，描述测试策略与用例组织 | 开发、测试 |
| [deployment-guide.md](deployment-guide.md) | 部署指南，说明部署与运维步骤 | 运维、开发 |
| [troubleshooting.md](troubleshooting.md) | 常见问题排查，提供故障定位与解决方案 | 运维、开发 |
| [CHANGELOG.md](CHANGELOG.md) | 变更记录，记录各版本变更历史 | 所有读者 |
| [README.md](README.md) | 本导航，文档入口与索引 | 所有读者 |

## 推荐阅读路径

- 新人入门：getting-started → SRS → high-level-design → coding-standards
- 接口开发：api-spec + coding-standards
- 部署上线：deployment-guide + troubleshooting

## 文档贡献约定

- 新增或修改文档须遵循统一格式：H1 标题 + 元信息表（文档版本 / 更新日期 / 状态）+ 章节编号。
- 修改文档后须同步更新本导航与 CHANGELOG.md。
- 图表统一放置于 assets/ 目录并在文档中引用。