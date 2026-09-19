# Codex Implementation Log

## 2026-09-18 阶段 0：工程骨架

- 分支：`feature`（未创建、切换或推送其他分支）。
- 范围：建立 pnpm workspace、Vue/Vite 前端空壳、NestJS API 空壳、共享包、PostgreSQL/Redis Compose、环境变量模板和质量配置。
- API 占位：`GET /api/health`。
- Web 占位：登录页和工作台页；未实现登录、线索、跟进或报表业务。
- 约束：阶段 0 初始化时未修改已审核的 `docs/prd.md`、`docs/er-diagram.md`、`docs/api.md`、`docs/roadmap.md` 和 `AGENTS.md`；后续版本核对阶段曾更新 `AGENTS.md` 的版本声明。
- 验收记录：Node `v22.20.0`、pnpm `11.19.0` 可用；Docker CLI 当前环境不可用，`docker compose up -d` 待具备 Docker 环境后重试。

## 阶段 0：仓库骨架初始化
- 提示词：按阶段 0 计划，只在 `feature` 分支搭建 pnpm workspace、Vue3/Vite 前端空壳、NestJS API 空壳、共享包、PostgreSQL/Redis Compose、环境变量、质量配置和阶段日志；不写业务逻辑，不修改已审核的五份 `docs` 文档。
- 生成文件：根目录 workspace/TypeScript/ESLint/Prettier 配置、`.env.example`、`docker-compose.yml`、`README.md`；`apps/web` Vue3 登录页与工作台占位；`apps/api` NestJS `GET /api/health`、Prisma 空 schema；`packages/shared` 共享常量和类型；`pnpm-lock.yaml`；本日志文件。
- 新增行数：8,344 行（按生成文件当前行数统计，其中 `pnpm-lock.yaml` 7,705 行，其余配置、源码和文档 639 行）。
- 运行命令：`pnpm install`；`pnpm lint`；`pnpm --filter api build`；`pnpm --filter web build`；`docker compose up -d`；`node apps/api/dist/main.js`；`Invoke-RestMethod http://localhost:3000/api/health`；`git diff --check`。
- 测试结果：`pnpm install` 完成；`pnpm lint` 通过；API build 通过；Web build 通过并生成 `apps/web/dist`；健康接口实际返回 `{ "status": "ok", "service": "api" }`；`git diff --check` 无代码空白错误；Docker Compose 未执行成功。
- 出现的问题：初始安装受 registry 权限和 pnpm 非交互模块清理影响；pnpm 供应链策略拒绝锁文件中的近期发布传递依赖；API 初次构建无法解析 workspace shared 类型；Web 初次构建缺少 Vue SFC 类型声明和 `@element-plus/icons-vue` 依赖；当前机器没有 Docker CLI。
- 如何修复：使用授权网络完成依赖安装；按 pnpm 11 的 `allowBuilds` 配置允许必要依赖安装脚本；给 shared 包补充 `main`/`types`；添加 `apps/web/src/env.d.ts` 并声明图标依赖；将根 `packageManager` 与实际 pnpm `11.19.0` 对齐；Docker 环境问题无法在本机修复，保留为验收阻断项。
- 人工修改：版本核对后将 `AGENTS.md` 的 Node/pnpm/TypeScript 及前后端依赖版本改为当前实际版本；未手工修改构建产物，未切换或推送分支，未提交或合并 PR。
- 下次改进：阶段开始前先确认 Docker CLI 和 registry 权限；固定并预热 pnpm 版本/依赖缓存后再执行验收；为 workspace 增加统一的依赖一致性检查，避免文档版本与 `package.json` 漂移。

## 阶段 1：数据库与共享类型

提示词：检查阶段 1 验收项；补齐 Prisma migration、seed 和共享类型引用；确认 build、lint、数据库表和日志状态；不新增业务 API 或前端页面。
生成文件：`apps/api/prisma/schema.prisma` 的 13 个模型；`apps/api/prisma/migrations/migration_lock.toml`；`apps/api/prisma/migrations/20260919000000_init/migration.sql`；`apps/api/prisma/seed.ts`；`packages/shared/src/{api,auth,dashboard,enums,followups,leads,tasks,users}.ts`；Prisma 脚本更新；本日志追加阶段 1 记录。
新增行数：2,330 行（按工作区相对当前基线的新增行数统计，包含 `pnpm-lock.yaml`；未计入构建产物）。
运行命令：`pnpm install --frozen-lockfile`；`pnpm --filter api prisma:format`；`pnpm --filter api prisma:validate`；`pnpm --filter api prisma:generate`；`pnpm --filter api prisma:migrate:dev -- --name init`；`pnpm --filter api prisma:seed`；`pnpm --filter api exec tsc --noEmit --target ES2022 --module commonjs --moduleResolution node --esModuleInterop --skipLibCheck prisma/seed.ts`；`pnpm lint`；`pnpm build`；`pnpm --filter api build`；`pnpm --filter web build`；`docker compose config`；`git diff --check`。
测试结果：依赖安装、schema format/validate、Prisma generate、seed TypeScript 编译、shared/API typecheck、API build、Web build、根 build、lint 和 `git diff --check` 通过；迁移静态包含 13 张表、4 个枚举、28 个外键及索引；共享类型可被 `apps/api/src/health.controller.ts` 引用；路由核对只有既有 `GET /api/health`，前端只有既有登录/工作台占位页；`prisma migrate dev` 因 `localhost:5432` 无 PostgreSQL 失败，seed 因无法连接同一数据库失败；`docker compose config` 因当前环境没有 Docker CLI 失败。
出现的问题：本机没有可连接的 PostgreSQL 16 实例，也没有 Docker CLI，因此无法在本环境实际应用 migration、执行 seed 或核对运行中的数据库表；全仓 `pnpm format:check` 仍报告既有文件格式差异。
如何修复：已新增可重复部署的初始 migration、`migration_lock.toml`、幂等 seed 和 `prisma:format`/`prisma:validate`/`prisma:migrate:dev` 脚本；在安装 Docker 并启动 PostgreSQL 后，重新运行 `docker compose up -d postgres redis`、`pnpm --filter api prisma:migrate:dev` 和 `pnpm --filter api prisma:seed` 完成环境级验收。
人工修改：未修改已审核的 `docs/prd.md`、`docs/er-diagram.md`、`docs/api.md`、`docs/roadmap.md`；未新增业务 Controller、Service、API 或前端页面；未创建、切换、推送其他分支，未提交或合并 PR。
下次改进：阶段开始前准备 Docker/PostgreSQL 验证环境；统一现有文件的 Prettier 行尾与格式后再启用 `pnpm format:check`；将 migration deploy、seed 和表清单检查加入 CI。
