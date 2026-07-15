# DineHub 贡献指南

感谢你改进 DineHub。本项目是模块化单体，变更应优先保持主链路可解释、可测试和可回滚，不以增加技术名词为目标。

## 开发环境

- JDK 17
- Node.js 22
- Docker Desktop（完整测试需要 MySQL、Redis、RabbitMQ 容器）
- 使用仓库内 Maven Wrapper，不依赖系统 Maven

推荐先执行：

```bash
docker compose up -d mysql redis rabbitmq
cd frontend/h5
npm ci
cd ../../backend
./mvnw clean verify
```

Windows PowerShell 使用 `./mvnw.cmd clean verify`。

## 分支与提交

1. 从最新 `main` 创建短生命周期分支，如 `feature/...`、`fix/...`、`docs/...`。
2. 一个 Pull Request 聚焦一个问题，避免混入无关格式化或重命名。
3. 提交信息使用 Conventional Commits，例如 `feat: ...`、`fix: ...`、`test: ...`、`docs: ...`、`chore: ...`。
4. 不直接重写或删除已经发布的 Flyway 迁移；数据库变化新增下一个 `V{n}__description.sql`。

## 修改前端

前端构建产物由后端 JAR 直接提供，因此源码和入库产物必须同步：

```bash
cd frontend/h5
npm ci
npm run build
git diff -- ../../backend/dinehub-server/src/main/resources/static/h5
```

最后一条命令用于审查生成产物；需要变化时将对应产物与源码一起提交，不需要变化时应保持无差异。CI 会在干净检出后重新构建，并用 `git diff --exit-code` 验证两者一致。

## 修改后端

```bash
cd backend
./mvnw clean verify
```

当前 CI 要求至少 96 个测试且 `skipped=0`。没有 Docker 时容器测试可能被跳过，这不能作为完整验证结果。

涉及并发、幂等、状态机或补偿的变更，应至少回答：

- 正确性的数据库约束或原子操作在哪里？
- 重复请求、重复消息和迟到消息怎样处理？
- 失败后由谁重试、何时终止、如何观察？
- 单实例、进程崩溃和数据卷丢失的边界分别是什么？

## Pull Request 要求

PR 描述应包含：问题、方案、影响范围、验证结果、风险与回滚方式。提交前确认：

- 没有真实密钥、Token、证书、个人数据或生产日志；
- 金额、权限和用户归属由服务端校验；
- 数据库变化使用新的 Flyway 迁移；
- 前端构建产物与源码一致；
- 测试完整执行且没有跳过容器测试；
- 性能数字来自可复现压测，不用估算值冒充实测；
- README、配置示例和实际行为保持一致。

## 安全问题

请不要通过公开 Issue 披露漏洞。报告方式见 [SECURITY.md](SECURITY.md)。
