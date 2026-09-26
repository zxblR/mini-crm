# Mini CRM 工程约定

本文件是 Mini CRM 仓库的强制工程约定。后续 ADR、接口契约或迁移方案与本文件冲突时，必须先更新本文件或记录明确的 ADR，再提交实现。

## 1. 项目定位

Mini CRM 面向小微团队管理客户线索、销售阶段、跟进记录和待办提醒。系统采用前后端分离、单仓库管理，默认协议为 REST/JSON。

本仓库当前只保留以下运行时：

- 前端：Vue 3 + TypeScript + Vite + Pinia + Element Plus + ECharts + Axios。
- 业务 API：Java 21 + Spring Boot 3 + Maven + Spring MVC + Bean Validation + Springdoc OpenAPI。
- AI 服务：Python 3.12 + FastAPI + Pydantic，仅提供内部 AI HTTP 能力。
- 数据库：PostgreSQL 16，宿主机默认端口 `55432`，容器内端口 `5432`。
- 数据访问：Java 使用 Spring JDBC/JdbcTemplate；禁止引入 ORM。
- 数据模型工具：Prisma 只维护根目录 `prisma/` 下的 schema 和 migration，不作为 Java API 运行时依赖。
- 基础设施：Docker Compose、PostgreSQL、Redis、nginx；BullMQ/ioredis 只在实际启用异步能力时使用。

## 2. 目录职责

```text
.
├─ apps/
│  ├─ web/                  # Vue 3 前端，仅访问 Java API
│  ├─ api/                  # Java Spring Boot 业务 API，唯一业务后端入口
│  └─ ai/                   # Python FastAPI 内部 AI 服务
├─ packages/
│  └─ shared/               # 仅供前端 TypeScript 使用的共享类型和常量
├─ prisma/
│  ├─ schema.prisma         # PostgreSQL 数据模型声明
│  └─ migrations/           # 可重复部署、不可篡改的数据库迁移
├─ infra/
│  ├─ docker/               # API/AI/migrate/mock-llm Dockerfile 等构建资产
│  └─ nginx/                # Web 反向代理配置
├─ docs/                    # PRD、ER、API、架构、路线图、汇报和演示脚本
├─ scripts/                 # 预检、维护和一次性工程脚本
├─ archive/                 # 非运行时的历史迁移参考，不加入 workspace
├─ docker-compose.yml       # Web/API/AI/数据库/Redis 本地服务编排
└─ package.json             # 根级 pnpm 和 Prisma CLI 脚本
```

边界规则：

- `apps/web` 不连接 PostgreSQL、Redis 或 FastAPI，只通过 nginx 同域 `/api/v1` 调用 `apps/api`；开发模式由 Vite proxy 转发。
- `apps/api` 是浏览器可访问的唯一业务 API。Controller 只负责协议适配、DTO 校验和权限声明；Service 负责业务规则；Repository/DAO 负责参数化 SQL 和数据库访问。
- `apps/api` 不使用 Prisma Client，不读取 `prisma/` 生成的运行时代码，也不在业务代码中拼接 SQL。
- `apps/ai` 不持有业务数据库连接，不保存业务状态，不绕过 Java API 的认证、组织隔离和敏感信息脱敏；内部端点使用 `AI_SERVICE_TOKEN`。
- `packages/shared` 只服务前端 TypeScript；Java DTO、Python Pydantic 模型和共享 TS 类型必须以 `docs/api.md` 为契约来源，不能复制粘贴成互不兼容的协议。
- `archive/` 仅保存旧技术栈的迁移参考，不参与 Compose、Maven、pnpm workspace 或生产镜像。
- Compose 的 `migrate` 一次性容器等待 PostgreSQL healthy 后执行根目录 Prisma migration；`SEED_ON_START=true` 才执行本地演示 seed。
- Compose 的 `web` 使用 Vue 构建产物和 nginx，宿主机默认通过 `8080` 访问；API、AI 和 mock-llm 仅加入 Compose 内网。
- AI 默认使用内部 `mock-llm`，生产切换到 OpenAI-compatible 服务时只通过环境变量提供 `LLM_BASE_URL`、`LLM_API_KEY` 和 `LLM_MODEL`。
- 已审核的 `docs/prd.md`、`docs/er-diagram.md`、`docs/api.md`、`docs/roadmap.md` 不因目录整理随意修改；阶段 10 文档收尾可在不改变业务结构的前提下更新 API、ER 和架构说明，若字段或架构发生明确冲突，先记录 ADR。

## 3. 分层与编码规范

### Web

- 页面放在 `apps/web/src/views`，路由放在 `router`，全局状态放在 Pinia stores，重复请求封装在 `src/api`。
- 组件优先使用 `<script setup lang="ts">`；TypeScript 开启 `strict`，禁止 `any`。
- Element Plus 是唯一 UI 组件库；图表使用 ECharts，组件卸载时销毁实例。
- 列表、表单、空态、加载态、错误态和权限不足状态必须完整。

### Java API

- 推荐按领域组织 `auth`、`users`、`leads`、`followups`、`tasks`、`dashboard`、`health` 等包。
- Controller 不写 SQL，不承载跨资源业务规则，不直接调用 FastAPI。
- Service 负责权限后的业务规则、事务边界、组织归属和错误码。
- Repository/DAO 集中封装 JdbcTemplate 查询；所有值使用参数绑定；排序字段只能来自服务端白名单。
- DTO 使用明确的 Java record/class 和 Bean Validation，禁止裸 `Object` 作为业务 DTO；响应统一为 `{ data, meta, error }`。
- 时间以 UTC 的 `Instant`/ISO 8601 传输；数据库时间字段使用 `timestamptz`。
- 密码、JWT secret、数据库 URL、Redis URL 和 AI 服务凭据只来自环境变量；日志不得记录密码、token、SQL secret、完整手机号或邮箱。

### Python AI

- FastAPI 路由只做协议适配，Pydantic 模型负责输入输出校验。
- AI 服务只处理 AI 输入和输出，不访问业务数据库，不保存业务状态。
- 内部接口必须有超时、可观测性和最小必要字段；不得把浏览器请求直接转发为未鉴权的 AI 请求。
- 当前 AI 能力包括意向评分、跟进摘要、下一步建议、话术生成和沉默唤醒；Java API 负责缓存、权限、超时、重试和错误映射。

## 4. Prisma 与 Java 数据模型对齐

- `prisma/schema.prisma` 是 PostgreSQL 表、列、枚举、约束和关系的声明来源；`prisma/migrations` 是部署来源。已应用到共享环境的 migration 不得修改，只能新增 migration。
- Prisma 仅用于 schema/migration 工具链；Java API 使用 JdbcTemplate 访问相同表，禁止在 `apps/api` 引入 Prisma ORM 或第二套 ORM。
- 数据库列使用 `snake_case`，Java 属性、DTO 字段和 API JSON 使用 `camelCase`；SQL 必须显式列出映射，不能依赖 `SELECT *`。
- UUID、可空性、长度、默认值、唯一约束、外键和枚举值必须与 schema 一一对应。Java 枚举的持久化值必须与 PostgreSQL/接口契约的字面值一致，不得只依赖 Java 常量名。
- `organization_id` 是组织隔离的必要条件。所有读写、关联和批量操作都必须在 SQL 或 Service 层校验组织归属，不能信任客户端传入的组织 ID。
- `timestamptz` 对应 Java `Instant`，API 使用 UTC ISO 8601；前端仅在展示层转换用户时区。
- JdbcTemplate 向 PostgreSQL 绑定时间参数时，不得直接传 `Instant`。统一通过 `JdbcTimeUtils.toDbTime(Instant)` 转成 UTC `OffsetDateTime` 后传参；读取 `timestamptz` 使用 `JdbcTimeUtils.fromDbTime(ResultSet, column)` 转回 `Instant`，保留空值语义。禁止依赖驱动对 `Instant` 的隐式 SQL 类型推断。
- 任何 `INSERT` 必须显式写入表中存在的 `created_at`/`updated_at` 时间列，不依赖数据库默认值，保证测试库、生产库和不同迁移状态下行为一致。仅有 `created_at` 或无时间列的表按实际 schema 执行。Mock `JdbcTemplate` 无法捕获 NOT NULL、唯一约束或外键错误；涉及写库的关键路径至少要在真实 PostgreSQL 上手动验证一次。
- 本项目不启用 Hibernate/JPA 自动建表或 `ddl-auto`。若未来新增 Java Entity，只能作为明确的读写映射模型，必须逐字段核对 Prisma schema，并由 migration 管理数据库结构。
- 结构整理不改变表、字段、索引、枚举或约束；需要变更时必须同时更新 schema、migration、Java 映射、API 契约和测试。

## 5. Java 调用 FastAPI

- 浏览器永远不直接调用 `AI_SERVICE_URL`；所有 AI 请求由 Java API 在服务端发起。
- Java 先完成 access token、RBAC、组织归属和资源可见性校验，再提取最小必要字段调用 FastAPI，并在调用前脱敏手机号、邮箱、token 等敏感信息。
- AI 地址只来自环境变量 `AI_SERVICE_URL`。调用必须设置连接/读取超时、限制请求体，按接口幂等性决定是否有限重试；禁止无限重试和静默降级为错误成功。
- FastAPI 返回值必须经过 Java DTO 校验和错误映射，不把内部堆栈、服务地址或供应商密钥返回给前端。
- Java 日志记录 request ID、用户 ID、组织 ID 和调用耗时，不记录完整 prompt、token 或客户敏感数据。

## 6. Git 规则

- 仓库只允许 `main` 和 `feature` 两个分支。开发、修复、文档和重构都在 `feature` 完成。
- 禁止创建、切换、推送任何其他分支；禁止直接 push `main`、force push 或自动合并 Pull Request。
- 提交使用 Conventional Commits：`feat:`、`fix:`、`docs:`、`refactor:`、`test:`、`chore:`；标题使用英文祈使句且不超过 72 个字符。
- 一个提交只解决一个可回滚主题；不得提交 `.env`、真实 token、客户数据、构建产物、coverage、数据库 dump 或临时文件。
- Pull Request 必须说明背景、方案、测试命令、迁移说明；涉及 UI 时附截图。合并前必须通过 CI。

## 7. 执行环境约束

### 允许（无需审批）

- 只读文件访问：ls、find、cat、type、Get-Content、dir
- git 只读：git status、git branch、git log、git diff、git show
- 只读检索：grep、Select-String、findstr

### 禁止（必须人工确认）

- git 写操作：add、commit、push、checkout、merge、branch -d、reset
- 文件写操作：rm、mv、cp、mkdir、Set-Content、Out-File、echo >>
- 构建运行：mvn、pnpm、docker、python、java -jar
- 数据库操作：psql、prisma migrate、prisma db push
- 禁止 push main、禁止 force push、禁止自动合并 PR
- 禁止创建、切换、推送任何其他分支

### 工作流

- Codex 负责：读文件、写代码、写文档、写测试、出计划。
- 人工负责：跑构建、跑测试、跑数据库、git 提交、PR 合并。
- Codex 生成代码后停止，等人工验收。

## 8. 验收命令

仓库根目录执行：

```powershell
pnpm install --frozen-lockfile
pnpm lint
pnpm format:check
pnpm typecheck
pnpm test
pnpm test:e2e
pnpm build
mvn -f apps/api/pom.xml test
pnpm -C apps/web lint
pnpm -C apps/web build
python -m compileall apps/ai
docker compose config
docker compose up -d postgres redis
docker compose down
```

本次结构整理的强制验收命令为：

```powershell
mvn -f apps/api/pom.xml test
pnpm -C apps/web lint
pnpm -C apps/web build
python -m compileall apps/ai
docker compose config
```

涉及认证、权限、迁移或关键流程时追加关键 E2E。验收失败必须修复或在实施日志中记录明确的环境阻断原因，不能以“本地可用”替代。

## 9. Definition of Done

目录边界、运行命令、Compose 路径、环境变量和文档保持一致；不引入新业务逻辑、不修改数据库结构；Java、Python、前端和 Prisma 的契约可追溯；日志和审计上下文可关联；强制验收命令通过。

## 10. 阶段 10 文档收尾

- 阶段 10 只允许修改 README、AGENTS 和 `docs/` 文档，不修改 Java、FastAPI、Vue、Prisma schema、migration 或运行时依赖。
- `docs/report.md` 的代码量必须由人工使用 cloc 统计；未提供结果前使用 `?` 占位，不凭估算填数。
- `docs/report.md` 必须区分源码测试数量、真实 PostgreSQL 验证、FastAPI/mock-LLM 验证和 E2E 验收；测试数不等于覆盖率。
- 演示账号只允许引用 `prisma/seed.sql` 中的本地演示账号；生产部署必须关闭 seed、替换默认密钥并删除示例凭据。
