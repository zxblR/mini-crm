# Mini CRM 工程约定

本文件是 Mini CRM 仓库的强制工程约定。若后续 ADR、接口契约或迁移方案与本文件冲突，必须先更新本文件或记录明确的 ADR，再提交实现。

## 1. 项目定位

Mini CRM 面向小微团队管理客户线索、销售阶段、跟进记录和待办提醒。系统采用前后端分离、单仓库管理，所有接口以 REST/JSON 作为默认协议。

## 2. 技术栈（基线版本）

版本以初始实现时的锁文件为准；未批准的替换不得进入主分支。

| 层次 | 固定技术 |
| --- | --- |
| 运行时与包管理 | Node.js 22.20.0 LTS、pnpm 11.19.0、TypeScript 5.7.2 |
| Web 前端 | Vue 3.5.13、Vite 6.2.5、Pinia 2.3.1、Element Plus 2.9.6、ECharts 5.6.0、Vue Router 4.5.0、Axios 1.8.4 |
| API 后端 | NestJS 11.0.11、TypeScript 5.7.2、class-validator（待接入）、Swagger/OpenAPI（待接入） |
| 数据访问 | Prisma 6.5.0；禁止在业务代码中直接拼接 SQL；复杂查询使用 Prisma `$queryRaw` 参数化调用 |
| 主数据库 | PostgreSQL 16-alpine（Docker 镜像） |
| 缓存与异步 | Redis 7.4-alpine（Docker 镜像）、BullMQ 5.41.5、ioredis 5.4.1 |
| 认证授权 | JWT（短期 access token + 可撤销 refresh token）、RBAC、bcrypt/argon2 密码哈希 |
| 部署 | Docker Compose v2；本地、CI、生产镜像均从 Dockerfile 构建 |
| 测试与质量 | Vitest、Vue Test Utils、Jest/Supertest（API）、Playwright（关键 E2E）、ESLint、Prettier |
| 观测 | Pino 结构化日志；请求 ID、用户 ID、组织 ID 必须可关联 |

禁止引入第二套 UI 组件库、状态管理库、ORM 或队列框架，除非有 ADR 和迁移计划。

## 3. 目录结构

```text
.
├─ apps/
│  ├─ web/                  # Vue3 + Vite 前端
│  │  └─ src/{api,assets,components,layouts,router,stores,views,types}
│  └─ api/                  # NestJS 后端
│     ├─ src/{auth,common,config,dashboard,leads,users,followups,health,main.ts}
│     ├─ prisma/{schema.prisma,migrations,seed.ts}
│     └─ test/{unit,e2e}
├─ packages/
│  ├─ contracts/            # API DTO、枚举、错误码等前后端共享类型
│  └─ eslint-config/        # 共享 lint 配置（可选，使用前需保持单一规则源）
├─ infra/
│  ├─ docker/               # Dockerfile、启动脚本
│  └─ compose/              # compose.override.yml 等环境编排
├─ docs/                    # PRD、ER、API、路线图、ADR
├─ .env.example
├─ docker-compose.yml
├─ package.json
├─ pnpm-workspace.yaml
└─ AGENTS.md
```

边界规则：

- `apps/web` 不直接访问数据库或 Redis，只调用 `packages/contracts` 描述的 HTTP API。
- `apps/api` 的 Controller 只做协议适配和权限声明，业务规则放在 Service，数据库访问集中在 Prisma service/repository。
- 跨应用共享的 DTO、枚举、错误码只能放在 `packages/contracts`，不可复制粘贴。
- 迁移文件一旦应用到共享环境不可修改，只能新增迁移。
- 文档中的接口路径、字段命名、枚举值必须与实现和 OpenAPI 保持一致。

## 4. 编码规范

- 文件统一 UTF-8；源码、配置和脚本默认 ASCII，产品中文文案按业务需要使用 UTF-8。
- 缩进 2 空格，单引号，分号，行尾 LF；由 Prettier 统一格式化。
- TypeScript 开启 `strict`；禁止 `any`，确需使用必须在同一行说明原因并限制范围。
- 变量、函数使用 `camelCase`；类型、类、Vue 组件使用 `PascalCase`；常量使用 `UPPER_SNAKE_CASE`；数据库字段使用 `snake_case`，Prisma 模型使用 `PascalCase` 并显式 `@map`。
- API 返回统一为 `{ data, meta, error }` 形状；分页使用 `page`、`pageSize`、`total`。
- 时间全部以 UTC 存储和传输，API 使用 ISO 8601；前端按用户时区展示。
- 所有写接口必须校验 DTO、权限和资源归属；列表接口必须支持分页，并设置最大 `pageSize`。
- 错误使用稳定的业务错误码，不向客户端返回堆栈、SQL 或密钥；日志记录上下文但脱敏手机号、邮箱、token。
- Vue 组件优先 `<script setup lang="ts">`；页面状态进入 Pinia，局部交互状态留在组件；重复请求封装在 `apps/web/src/api`。
- ECharts 配置放在组件或专用 composable 中，图表实例在卸载时销毁；表格、表单、空态、加载态、错误态必须完整。
- 密码、JWT secret、数据库 URL、Redis URL 只能来自环境变量；不得提交 `.env`、真实 token 或客户数据。

## 5. Git 规范

- 分支固定为 `main` 和 `feature`：`main` 为可发布主分支，`feature` 为唯一开发分支；功能、修复、文档和重构均提交到 `feature`，通过 Pull Request 从 `feature` 合并到 `main`。未经 ADR 批准不得创建其他长期分支，也不得直接向 `main` 提交或推送。
- Commit 使用 Conventional Commits：`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`；标题祈使句、英文、不超过 72 个字符。
- 一个提交只解决一个可回滚主题；不要提交构建产物、coverage、`.env`、数据库 dump 或临时文件。
- Pull Request 必须包含背景、方案、测试命令、数据库迁移说明和截图（涉及 UI 时）。
- 合并前必须通过 CI；禁止绕过 hooks、强推 `main`、把未审查的自动生成代码直接合并。

## 6. 验收命令

以下命令是基线脚本名称；初始化项目时必须在根 `package.json` 中提供对应脚本。

```powershell
pnpm install --frozen-lockfile
pnpm lint
pnpm format:check
pnpm typecheck
pnpm test
pnpm test:e2e
pnpm build
docker compose config
docker compose up -d postgres redis
pnpm --filter api prisma:generate
pnpm --filter api prisma:migrate:deploy
docker compose down
```

本地提交前至少运行 `pnpm lint; pnpm format:check; pnpm typecheck; pnpm test`。涉及认证、权限、迁移或关键流程时必须追加 `pnpm test:e2e`。CI 失败不得以“本地可用”替代修复。

## 7. Definition of Done

功能只有同时满足以下条件才算完成：需求和权限已更新；DTO、错误码和 OpenAPI 已更新；有单元测试，关键用户流程有 E2E；迁移可重复部署；日志和审计字段齐全；上述验收命令通过；文档与实现一致。
