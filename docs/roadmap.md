# Mini CRM Roadmap

阶段按 0～10 编号推进。每阶段完成后才能进入下一阶段；命令均在仓库根目录执行。命令是验收门槛，不代表当前仓库已经具备这些脚本。

## 阶段 0：基线与决策

目标：确认 Java Spring Boot 业务后端、Python AI 服务、Vue 前端、环境变量、编码规范、分支和 API/ER 契约；建立最小可运行的 monorepo 壳。

验收：`pnpm install --frozen-lockfile`; `pnpm lint`; `pnpm typecheck`; `docker compose config`。

## 阶段 1：本地基础设施

目标：Docker Compose 启动 Java API、Python AI、PostgreSQL、Redis；健康检查、环境变量模板、API/Web 空壳和基础日志可用。

验收：`docker compose up -d postgres redis`; `docker compose ps`; `curl http://localhost:8080/api/v1/health`; `docker compose down`。

## 阶段 2：数据模型与迁移

目标：由 Java 数据访问层使用 PostgreSQL 迁移实现组织/用户/角色/阶段/线索等核心表、索引、种子数据和可重复迁移。

验收：`cd apps/api; mvn test`; `docker compose up -d postgres redis`; `python -m compileall apps/ai`。

## 阶段 3：认证与 RBAC

目标：登录、刷新、退出、密码哈希、JWT rotation、组织隔离、Admin/Sales/Viewer 守卫和审计基础能力。

验收：`cd apps/api; mvn test`; `pnpm test:e2e -- auth`; `curl -i -X POST http://localhost:8080/api/v1/auth/login`；验证无 token 为 `401`、越权为 `403`。

## 阶段 4：线索核心 CRUD

目标：线索列表筛选、创建、详情、编辑、归档/恢复、分配、去重校验和统一错误码；前端完成列表/表单/详情。

验收：`cd apps/api; mvn test`; `pnpm --filter web test`; `pnpm test:e2e -- leads`; 手工验证分页最大 `pageSize=100` 和跨组织资源返回 `404`。

## 阶段 5：阶段看板与跟进时间线

目标：阶段配置、阶段变更历史、拖拽/按钮推进、跟进记录和详情时间线；赢单/输单规则完整。

验收：`cd apps/api; mvn test`; `pnpm test:e2e -- pipeline`; `pnpm --filter web build`；验证输单无原因不能提交。

## 阶段 6：任务、提醒与 BullMQ

目标：下一步任务、完成/取消/改期、逾期查询、Redis 队列 worker 和幂等提醒作业。

验收：`docker compose up -d postgres redis`; `cd apps/api; mvn test`; `pnpm test:e2e -- tasks`；验证 Java 队列消费者日志。

## 阶段 7：仪表盘与 ECharts

目标：摘要、漏斗、来源、负责人排行和逾期指标 API；前端用 ECharts 展示筛选后的数据和加载/空态/错误态。

验收：`cd apps/api; mvn test`; `pnpm --filter web test -- dashboard`; `pnpm test:e2e -- dashboard`; `pnpm --filter web build`。

## 阶段 8：导入、审计与可运维性

目标：CSV 异步导入、任务状态、关键写操作审计、结构化日志、request ID、健康/就绪检查和基础限流。

验收：`pnpm test:e2e -- import audit`; `curl http://localhost:8080/api/v1/ready`; `docker compose config`; 验证非法 CSV 不污染已有数据。

## 阶段 9：质量、性能与安全加固

目标：补齐边界测试、权限矩阵、SQL/索引检查、备份恢复演练、依赖漏洞扫描和核心列表性能基线。

验收：`pnpm lint`; `pnpm format:check`; `pnpm typecheck`; `pnpm test`; `pnpm test:e2e`; `pnpm build`; `pnpm audit --prod`；并记录 p95 测试结果。

## 阶段 10：发布与 MVP 验收

目标：生成版本候选、生产 Compose 配置、迁移发布流程、回滚说明、用户验收脚本和发布文档；确认 PRD 的 MVP 边界没有越界。

验收：`docker compose -f docker-compose.yml config`; `docker compose up -d`; `cd apps/api; mvn test`; `pnpm test:e2e`; `docker compose down`；由 Admin、Sales、Viewer 各完成一次登录→线索→跟进→看板验收。

## 每阶段的共同完成条件

代码、测试、迁移、OpenAPI 和相关文档在同一 PR 内更新；CI 通过；无未解释的 lint/type 错误；涉及 UI 必须附桌面截图；涉及权限必须附允许/拒绝用例；不得把真实客户数据用于测试或提交。
