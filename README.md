# Mini CRM

Mini CRM 是面向小微团队的客户线索管理系统。当前仓库处于阶段 0：只提供可构建的前后端骨架、健康检查、共享类型和本地基础设施，不包含业务逻辑。

## 技术栈

- Web：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、ECharts、Axios
- API：NestJS、TypeScript、Prisma、PostgreSQL、Redis、BullMQ
- 鉴权基线：JWT + RBAC（后续阶段实现）
- 部署：Docker Compose

## 目录

```text
apps/web        Vue 前端，占位登录页和工作台
apps/api        NestJS API，GET /api/health
packages/shared 前后端共享类型和常量
docs            已审核产品、数据模型、API 和路线图文档
```

## 本地启动

1. 复制环境变量：`Copy-Item .env.example .env`。
2. 启动基础设施：`docker compose up -d`。
3. 安装依赖：`pnpm install`。
4. 启动 API：`pnpm --filter api start:dev`，健康检查地址为 `http://localhost:3000/api/health`。
5. 启动 Web：`pnpm --filter web dev`，打开 `http://localhost:5173`。

## 阶段 0 验收

```powershell
pnpm install
pnpm lint
pnpm --filter api build
pnpm --filter web build
docker compose up -d
```

阶段日志见 `docs/codex-log.md`。当前阶段不要求真实登录、线索 CRUD、数据库迁移或队列业务行为。
