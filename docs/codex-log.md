# Codex Implementation Log

> 说明：早期阶段条目按当时实际环境保留，其中的旧路径、旧端口和 NestJS 描述属于历史记录；当前文档统一使用 `apps/api`、Java Spring Boot、Java API `8080` 和 PostgreSQL 宿主端口 `55432`。

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

## 阶段 2：后端基础设施与认证授权（旧 NestJS 实现）

- 分支：`feature`；未创建、切换、推送其他分支，未直接推送 `main`，未 force push，未自动合并 PR。
- 范围：JWT 登录与当前用户、全局鉴权、RBAC、统一响应、异常过滤、ValidationPipe、Swagger、用户接口、团队接口和 activity logs；未实现线索、跟进、任务、统计、AI 或前端页面。
- 主要文件：`apps/api/src/{auth,users,teams,common,prisma}`、`apps/api/src/{app.module.ts,main.ts}`、`packages/shared/src/{auth,enums,users,api}.ts`、Prisma schema/migration、`docs/codex-log.md`。
- 认证：access token 使用 JWT，密码使用 argon2；响应不包含 `passwordHash`。
- 验收结果：`pnpm --filter api prisma generate`、`pnpm --filter api build`、`pnpm lint`、`pnpm --filter api test` 和额外的 `pnpm --filter api test:e2e` 均通过；单测 2 项通过，认证 HTTP e2e 2 项通过。
- 注意：尚未在本机启动 PostgreSQL/Docker 实例执行真实数据库迁移和端到端登录；本阶段验证使用 Prisma Client 生成、TypeScript 构建、Guard 单测和 mock 服务 HTTP e2e。

## 阶段 3：后端语言迁移

- 决策：业务 API 迁移到 Java 21 + Spring Boot 3；Vue 前端保持不变；AI 能力拆分为 Python 3.12 + FastAPI 内部服务。
- 新入口：`apps/api-java` 监听 `3000`，`apps/ai` 监听 `8000`；前端继续使用 `VITE_API_BASE_URL` 访问 Java API。
- 数据库：沿用既有 PostgreSQL 迁移 SQL，后续由 Java 迁移工具接管；旧 `apps/api` NestJS 代码暂存为迁移参考，不再作为 Compose API 服务。
- 验证限制：当前机器未安装 Java/Maven，Java 编译和 Spring Boot 测试待安装 JDK 21、Maven 3.9+ 后执行。

## 阶段 3：Java 业务后端重构

- 分支：`feature`；未创建、切换、推送其他分支，未直接推送 `main`，未 force push，未自动合并 PR。
- 范围：`apps/api-java` 接管认证、用户、团队、线索、阶段、跟进、任务、标签、仪表盘、导入任务、审计和健康检查；Vue 前端与 Python AI 服务保持独立。
- 认证授权：JWT access/refresh token、refresh token 轮换与撤销、Argon2 密码哈希、`@Public` 公开接口拦截器、全局认证拦截器、`@Roles` RBAC、OWNER/ADMIN/SALES/SUPPORT 组织隔离。
- 业务能力：完成线索 CRUD、归档/恢复、分配与阶段历史；跟进 CRUD 与下一步任务；任务查询、完成、取消和终态保护；阶段、标签、仪表盘、CSV 异步导入、审计日志和 `/ready` 数据库探针。
- 工程调整：Java 使用 `JdbcTemplate` 参数化访问既有 PostgreSQL schema；成功/失败响应统一为 `{ data, meta, error }`；所有响应不暴露 `passwordHash`；Docker Compose API 服务切换到 Java 并挂载导入文件卷；前端无业务页面改动。
- 测试结果：
  - `mvn "-Dmaven.repo.local=D:\codex\小微团队客户线索管理与智能跟进系统\.m2\repository" test`：9 个 Java 测试通过。
  - `mvn "-Dmaven.repo.local=D:\codex\小微团队客户线索管理与智能跟进系统\.m2\repository" package -DskipTests`：通过，生成 `apps/api-java/target/mini-crm-api-0.1.0.jar`。
  - `pnpm lint`、`pnpm typecheck`、`pnpm test`、`pnpm build`：通过。
  - `pnpm format:check`：仍受仓库既有 44 个文件格式差异阻断；未执行全仓格式化，避免改写已审核文档。
- 环境限制：2026-09-19 本机没有 Docker CLI，`localhost:5432` 没有 PostgreSQL；无法执行真实 migration/seed、数据库登录 E2E 和 `docker compose config`。Spring 上下文与 MockMvc 已验证健康接口、统一 request ID、公开路由和未认证 401。

## 2026-09-20：重构后环境复验

- 分支：`feature`；未创建、切换、推送其他分支，未直接推送 `main`，未 force push，未自动合并 PR。
- 环境：Java `21.0.12.1`、Maven `3.9.16`、Node `22.20.0`、pnpm `11.19.0` 可用；当前执行环境仍找不到 Docker CLI，Python 3.12 也未安装到 PATH。
- 数据库：`localhost:5432` 的 TCP 端口可连接，但当前默认 Prisma 凭据执行 `prisma migrate deploy/status` 均在 schema engine 阶段失败，未执行 seed；未确认该实例是否为 Mini CRM 数据库。
- 通过项：Java Maven 测试 9 项全部通过；Java 打包通过；`pnpm lint`、`pnpm typecheck`、`pnpm test`、`pnpm build` 通过。
- 运行时限制：Java Spring 容器可完成初始化，但当前 Windows/JDK 执行器启动 Tomcat 时在 NIO loopback 管道返回 `java.net.SocketException: Invalid argument: connect`，因此未完成真实 HTTP 登录、权限和 Swagger 验证。
- 配置修正：新增 `JAVA_DATABASE_URL`，将 Java JDBC 地址与 Prisma 使用的 `DATABASE_URL` 分离；Compose API 改用 `JAVA_DATABASE_URL`；根 `pnpm test/build/test:e2e` 改为包含 Java Maven 验收；README 补充迁移、seed 和 Java JDBC 配置步骤。
- 后续验收：在具备 Docker CLI、Python 3.12 和可确认凭据的环境中执行 `docker compose config`、`docker compose up -d`、Prisma migration/seed、真实登录/权限接口测试和 `docker compose down`。

## 2026-09-20：Java、Docker 与 Python 运行链路复验

- 分支：`feature`；未创建、切换、推送其他分支，未直接推送 `main`，未 force push，未自动合并 PR。
- 当前运行时：Docker `29.8.0`、Docker Compose `v5.5.1`、Python `3.12.10`、FastAPI `0.115.11`、Uvicorn `0.34.0`、Java `21.0.12.1`、Maven `3.9.16`。
- 宿主机已有非本项目 PostgreSQL 占用 `5432`，数据库为 `community`，账号为 `postgres`；未停止或修改该容器。本项目默认宿主映射端口统一调整为 `55432`，容器内部仍使用 PostgreSQL `5432`。
- Compose：`docker compose config` 通过；Java API 和 AI 镜像构建通过；PostgreSQL、Redis、Java API、AI 容器均成功启动，Redis healthy，PostgreSQL healthy，Java API 监听 `3000`，AI 监听 `8000`。
- 数据库：`pnpm --filter api prisma:migrate:deploy` 在本项目 `55432` 数据库成功应用 1 个 migration；seed 通过 `node --experimental-strip-types prisma/seed.ts` 成功写入演示团队和 3 个用户。直接使用 `tsx` 曾因 Windows Node `uv_os_get_passwd returned ENOMEM` 失败，但不是 seed 或数据库错误。
- HTTP 验证：`GET /api/health`、`GET /api/ready`、`GET /api/docs`、AI `GET /docs` 均成功；OWNER 登录、`/api/auth/me`、用户列表和当前团队成功；SALES 访问用户列表返回 `403`；无 token 访问受保护接口返回 `401`；未知字段返回 `400`；AI `POST /v1/follow-up-suggestions` 返回 `200`。
- 安全与审计：登录响应包含 access/refresh token，不返回 `passwordHash`；数据库 `activity_logs` 已记录 3 条 `LOGIN` 操作。
- 测试：Java Maven 测试 9/9 通过；`pnpm lint`、`pnpm typecheck`、`pnpm test`、`pnpm build`、`pnpm --filter api prisma:generate`、Python `compileall`、`git diff --check` 和 Compose 配置检查通过。
- 当前仍存在的环境注意事项：本机用户级 Maven 配置位于不可写的 `C:\.m2`，直接运行 `mvn` 可能失败；使用工作区可写 Maven 仓库或 Docker 构建可通过。共享登录类型已兼容 Java 返回的 refresh token，README seed 命令已修正。

## 2026-09-20：按实际技术栈重构目录

- 分支：`feature`；未创建、切换、推送其他分支，未直接 push `main`，未 force push，未自动合并 PR。
- 目录移动：
  - Java Spring Boot 服务由 `apps/api-java` 移至 `apps/api`。
  - 旧 NestJS 运行时由 `apps/api` 移至 `archive/api-nest`，不再加入 pnpm workspace 或 Compose。
  - Prisma schema 和 migration 由旧 API 目录移至根目录 `prisma/`；旧 seed 脚本保留在归档目录，根 Prisma 目录只维护 schema/migration。
  - API/AI Dockerfile 移至 `infra/docker/`，Compose 改用仓库根上下文和新 Dockerfile 路径。
- 文档与配置：更新 `AGENTS.md`、`README.md`、`.env.example`、`.gitignore`、根 pnpm 脚本和 workspace 构建许可；新增 `docs/architecture.md` 和 `scripts/codex-preflight.sh`。未修改已审核的 `docs/prd.md`、`docs/er-diagram.md`、`docs/api.md`、`docs/roadmap.md`。
- 数据库：未修改 schema、migration、表、字段、索引、枚举或约束；PostgreSQL 宿主端口保持 `55432`。
- 验收结果：
  - `mvn -f apps/api/pom.xml test`：通过，9 个测试全部通过。
  - `pnpm -C apps/web lint`：通过。
  - `pnpm -C apps/web build`：通过，Vite 产物生成成功。
  - `python -m compileall apps/ai`：通过；由于当前 PowerShell PATH 未注册 Python，使用本机 Python 3.12 可执行文件并临时扩展 PATH 执行同一命令。
  - `docker compose config`：通过；由于当前 PowerShell PATH 未注册 Docker，使用本机 Docker CLI 并临时扩展 PATH 执行同一命令，未启动容器。
  - `pnpm install --frozen-lockfile`、`pnpm lint`、`pnpm typecheck`、`git diff --check`、`D:\Git\bin\bash.exe -n scripts/codex-preflight.sh`：通过。
- 风险与后续：`docs/roadmap.md` 的历史验收文字仍保留 `apps/api-java` 路径，遵守本次不改已审核文档的约束；当前结构和启动命令以 `AGENTS.md`、`README.md`、`docs/architecture.md` 为准。未执行数据库迁移、真实登录 E2E 或 Compose 启动验收，本次仅做目录和构建链路整理。

## 2026-09-20：文档路径与端口对齐

- 分支：`feature`；未创建、切换、推送其他分支，未直接 push `main`，未 force push，未自动合并 PR。
- 更新文档：`docs/roadmap.md`、`docs/prd.md`、`docs/api.md`、`docs/er-diagram.md`、`docs/architecture.md`。
- 对齐内容：现行文档统一使用 `apps/api`、Java Spring Boot、Java API `8080` 和 PostgreSQL 宿主端口 `55432`；未修改产品流程、字段设计、数据库结构或代码。
- 历史记录：`docs/codex-log.md` 中早期 NestJS、`apps/api-java` 和 `localhost:3000` 条目保留为历史事实，并在文档说明中标注。
- 检查：非历史文档不再包含旧路径或旧端口；残留项仅位于本日志的历史段落。

## 2026-09-20：阶段 3 线索模块 Java 实现

- 分支：`feature`；未创建、切换或推送其他分支，未直接推送 `main`，未 force push，未自动合并 PR。
- 持久化：沿用 `JdbcTemplate` + 参数化 SQL；新增固定 `LeadStatus` 状态机、规范化联系人去重、持久化 `import_jobs` 队列、Commons CSV 导入和流式 CSV 导出。
- 路由与权限：业务路由统一为 `/api/v1/*`；Swagger 保持 `/api/docs*`；`OWNER/ADMIN/SALES/SUPPORT` 保持既有角色，SUPPORT 只读且不可导出。
- 兼容策略：保留 `pipeline_stages`、`leads.stage_id`、旧 stage history 列和旧终态字段；新代码只使用 `status`、`closed_at`、`outcome_note`、`lost_reason` 及 `from_status/to_status`。Pipeline stage 类保留为阶段 3.5 预留但不再注册运行时路由。
- 审计与安全：线索写操作、导入排队和导出请求写入事务内操作日志；导出执行公式注入转义和 ASCII 文件名；导入错误不保留未脱敏 PII。并发唯一索引冲突映射为 `409 LEAD_DUPLICATE`。
- 测试：`mvn -f apps/api/pom.xml test` 通过，21 项测试 0 failures / 0 errors；新增状态机、联系人规范化、导入表头、导出公式注入和线索 MockMvc 契约测试。
- 验收：`pnpm install --frozen-lockfile`、`pnpm lint`、`pnpm typecheck`、`pnpm -C apps/web lint`、`pnpm -C apps/web build`、`pnpm prisma:validate`（显式注入 DATABASE_URL）、`pnpm exec prisma generate`（通过本地 `.bin` shim）、`docker compose config` 和 `git diff --check` 均通过。当前环境的 `python`/`py -3.12` 未注册，使用已安装 Python 3.7.4 执行 `compileall` 成功；未声称其满足 Python 3.12 版本门槛。
- 环境：Maven 首次依赖下载受沙箱网络限制，使用授权执行后通过；Docker CLI 位于 Docker Desktop 安装目录而未加入 PATH，使用绝对路径完成 Compose 配置校验。

## 2026-09-21：阶段 3 本机验收与提交前修复

- 分支：`feature`；未创建、切换或推送其他分支，未直接推送 `main`，未 force push，未自动合并 PR。
- 本机验收：Codex 沙箱无终端执行能力，验收改由本机 PowerShell 执行。
  - `mvn -f apps/api/pom.xml test`：21/21，BUILD SUCCESS，0 failures / 0 errors。
  - `pnpm -C apps/web lint`、`pnpm -C apps/web typecheck`、`pnpm -C apps/web build`：通过（34 模块，2.04s）。
  - `python -m compileall apps/ai`：通过。
  - `docker compose config`：通过，API 端口 8080，PostgreSQL 宿主 55432。
  - `git diff --check`：无空白错误（仅 LF/CRLF 行尾警告）。
- 人工修复：
  - `ImportJobService.java`：多构造函数未标 `@Autowired`，Spring 无法确定注入构造函数，测试阶段暴露；在三参构造函数加 `@Autowired`。
  - `ExportService.java`：同样问题；在两参构造函数加 `@Autowired`。
  - 仅修这两处，未改业务逻辑。
- 环境修正：Git `safe.directory` 警告，已通过 `git config --global --add safe.directory "D:/codex/小微团队客户线索管理与智能跟进系统"` 修正。
- 下次改进：生成多构造函数 Service 时默认加 `@Autowired`；生成代码后立即跑 `mvn test`，不要把编译期问题留到验收；验收命令由本机执行，结果粘回 Codex。

## 2026-09-22：阶段 4 跟进、任务与 Timeline 扩展

- 范围：补齐 FollowUp 软删除后的 `nextFollowUpAt` 事务重算、自动任务幂等、负责人无效跳过告警、任务今日列表、任务提醒超时扫描、Timeline UNION ALL 分页和共享 TypeScript 类型。
- 事件：新增 `ActivityLogEvents` 常量类，集中维护 `CREATE_FOLLOW_UP`、`UPDATE_FOLLOW_UP`、`DELETE_FOLLOW_UP`、`CREATE_TASK`、`UPDATE_TASK`、`COMPLETE_TASK`、`CANCEL_TASK`、`SKIP_TASK_AUTO_CREATE` 及负责人原因值。
- Timeline：阶段历史、跟进、任务操作日志统一使用 `UNION ALL`，按 `occurred_at DESC, created_at DESC, id DESC` 排序并使用 `LIMIT/OFFSET`；软删除跟进保留并标记 `deleted: true`，普通 FollowUp 列表继续过滤软删除。
- 调度：新增 `TaskReminderScheduler`，默认关闭，生产通过 `app.scheduler.task-reminder.*` 显式开启；扫描使用 `FOR UPDATE SKIP LOCKED`，仅领取 pending 且尚未提醒的超时任务。
- 验收：本次未在 Codex 中运行 Maven、pnpm、Python、Docker 或数据库命令；需由人工执行下方验收命令并记录结果。

## 2026-09-22：阶段 4 验收补测与接口偏差固化

- 接口契约：确认 FollowUp 创建/列表使用嵌套路由，Task 完成使用 `POST /tasks/{id}/complete`，Timeline 合并在 `GET /leads/{id}` 的 `timelinePage/timelinePageSize` 查询参数中。
- 测试：新增 FollowUp 自动任务幂等、软删除重算、负责人无效跳过日志、Timeline SQL 聚合契约、Task 权限/终态和 Task 操作日志测试。
- ImportJobWorker：增加 `app.import.enabled` 条件，默认开启；测试 profile 关闭，避免 Spring 测试上下文后台轮询真实数据库。

## 2026-09-22：阶段 4 验收修复

- 修复 FollowUp 自动任务查询的 UUID 映射，兼容 JDBC 返回的 UUID 或字符串值。
- 修正 FollowUpService 测试中的 JdbcTemplate 泛型 stub，避免将 FollowUp `LinkedHashMap` 错当作 Task UUID。
- 清理该测试的 raw Mockito matcher，消除 unchecked 警告。

## 2026-09-23：JDBC timestamptz 参数映射修复

- 原因：PostgreSQL JDBC 驱动无法为直接绑定的 `java.time.Instant` 推断 SQL 类型，可能令 FollowUp 创建返回 500。
- 修复：新增 `JdbcTimeUtils`，所有本轮扫描到的 `Instant` SQL 参数统一转成 UTC `OffsetDateTime`；所有 API 结果映射中的 `timestamptz` 列显式读取为 `OffsetDateTime` 并还原 `Instant`，保留 `NULL`。
- 覆盖：FollowUp 创建/更新/自动任务、Lead 创建/更新、导入、导出日期过滤、Task 更新及 nextFollowUpAt 重算；新增 UTC 转换与读取测试，并在 FollowUp JDBC 参数测试中断言实际绑定类型。
- 验收：遵循仓库人工验收规则，本轮未运行构建或测试；待人工执行 `mvn -f apps/api/pom.xml test`。

## 2026-09-23：阶段 4 测试断言修复

- 修复 FollowUpService 更新参数类型测试中 Lead 重算时间的期望值。测试的 nextFollowUpAt RowMapper stub 固定返回 `2026-09-30T00:00:00Z`，因此 Lead UPDATE 参数应与该 stub 返回值一致；FollowUp 和自动 Task 更新参数仍断言请求时间 `2026-10-01T09:30:00Z`。
- 静态确认 SQL 子串和参数下标分别指向 FollowUp UPDATE、Task INSERT、Lead UPDATE；未改生产代码。测试待人工重新运行。

## 2026-09-23：阶段 4 FollowUp INSERT 约束修复

- 真实 PostgreSQL 验证发现 `follow_ups.updated_at` 为 NOT NULL 且无默认值，FollowUp 创建 INSERT 未写入该列，导致 `POST /api/v1/leads/{leadId}/follow-ups` 返回 500。
- 修复：FollowUp INSERT 显式写入 `created_at`、`updated_at`；自动 Task INSERT 以及 Lead 创建/导入 INSERT 同步显式写入两列，避免依赖数据库默认值。
- 扫描结论：确认遗漏为 FollowUp INSERT 的 `updated_at`（并按约定补齐相关已存在时间列）；activity_logs、lead_tags、stage_history 等表按其实际 schema 的时间列处理，未修改 migration 或阶段 3 业务逻辑。
- 重要性：Mock JdbcTemplate 无法模拟 NOT NULL、唯一约束和外键错误；FollowUp、Task、Lead、Import 等写库关键路径必须在真实 PostgreSQL 上至少手动验收一次。

## 2026-09-23：阶段 5 统计报表实现待人工验收

- 分支：按本轮开始前人工确认的当前分支 `feature` 工作；未执行 git 命令、未创建/切换其他分支、未提交或推送。
- 范围：按 ADR 0002 扩展现有 dashboard 统计 API；只做数量/时长，不实现 AI、前端页面、金额、成本、ROI 或导出；未修改 migration。
- PostgreSQL 集成测试只读取 `MINI_CRM_TEST_DATABASE_URL` 和独立用户名/密码环境变量，并拒绝数据库名不是 `mini_crm_test` 的 URL；在该库单个事务中插入少量合成组织、用户、线索和跟进，调用真实 DashboardStatsService 校验公式，最终强制回滚，不保留测试数据。
- 验收：本轮未运行 shell、构建、测试、Docker 或数据库命令。人工验收待执行；统计索引需先在 `mini_crm_test` 用合成数据采集 `EXPLAIN (ANALYZE, BUFFERS)`，当前未新增索引 migration。
## 2026-09-24：阶段 6 AI 智能跟进实现

- 分支：沿用 feature；本轮未创建、切换、提交或推送其他分支。
- 范围：新增 AI suggestion Prisma model、不可变 migration、FastAPI 内部五类能力、Java 同步调用/缓存/RBAC/审计、共享 TypeScript 类型和契约测试；未写前端页面，未修改阶段 3/4/5 业务逻辑或既有 migration。
- 决策：Roadmap 阶段 5.5 为金额报表 backlog，阶段 6 为 AI，阶段 6.5 为清理 backlog，阶段 7～10 调整为前端骨架、业务页面、联调 Docker、文档 cloc 汇报；SUPPORT 仅可读 AI 结果；Java RestClient 2s/8s/10s 同步预算；PostgreSQL 承担缓存，不引 Redis；默认 DeepSeek OpenAI-compatible，测试使用 mock LLM；旧 FastAPI 路由保持兼容。
- 隐私：上下文在 Java 侧移除联系方式和负责人标识；操作日志只保存类型、prompt 版本、模型、缓存结果、延迟和错误码等非 PII metadata。
- 验收：遵循人工验收规则，本轮未执行 shell、Maven、pnpm、Python、Docker、数据库或测试命令。必须由人工完成 mvn -f apps/api/pom.xml test、python -m compileall apps/ai、前端 lint/build、docker compose config，并在独立 PostgreSQL 与真实 FastAPI + mock LLM 环境完成端到端验证。
## 2026-09-24：阶段 6 AI 智能跟进

- 依据 ADR 0003 固化 Roadmap、权限、超时、缓存、LLM 供应商和 FastAPI 兼容决策。
- 新增 `ai_suggestions` schema/migration、Java AI 调用链、FastAPI 内部端点、共享 TS 契约和 prompt 文件。
- 未执行 shell 命令、构建、测试、数据库操作或 Git 写操作；待人工验收。

## 2026-09-25：阶段 9 Compose 一键启动

- 分支：`feature`；仅执行只读检查和文件编辑，未创建、切换、提交或推送其他分支。
- A～E 检查：确认现有 Compose 只有 API、AI、PostgreSQL、Redis；仅有 API/AI Dockerfile；Web Vite proxy 指向本地 API 且 Axios 默认 `/api/v1`；FastAPI 入口为 `main:app`；根 Prisma migration 位于 `prisma/migrations`。
- 变更：新增 `migrate` 一次性容器、Web 多阶段 Dockerfile、nginx 反代、内网 `mock-llm`、Compose 健康依赖和 Web/数据库/Redis 端口映射；`.env.example`、README 和部署拓扑同步更新。
- LLM：Compose 默认 `LLM_BASE_URL=http://mock-llm:9000`，配置真实 OpenAI-compatible DeepSeek 地址和 key 即可切换；浏览器仍不直接访问 AI。
- Seed：`SEED_ON_START` 默认 `false`，仅迁移容器执行开发 seed；生产部署不建议开启。
- 验收：本轮未运行 Docker、Maven、pnpm、Python 或数据库命令；等待人工执行阶段 9 验收命令。
