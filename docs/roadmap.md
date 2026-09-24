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

## 阶段 5：统计报表（本次）

目标：扩展现有 `/api/v1/dashboard/*` 数量统计，提供总览、渠道、销售排名、趋势、流失原因和响应时长代理指标；统一 UTC 统计与组织/个人数据范围。不做金额、成本、ROI、AI 或前端页面。

验收：`mvn -f apps/api/pom.xml test`；MockMvc 验证 401/403/404/正常流；使用独立 `mini_crm_test` 真实 PostgreSQL 验证聚合与时间类型；合成数据执行 `EXPLAIN (ANALYZE, BUFFERS)`，仅在核心表出现有实质影响的 Seq Scan 时考虑新增索引 migration。

## 阶段 5.5：金额报表（backlog）

目标：未来在明确金额字段、数据来源和统计口径后规划金额报表；本阶段不实施，不影响阶段 6。

## 阶段 6：AI 智能跟进（本次）

目标：增加意向评分、跟进摘要、下一步建议、话术生成和沉默唤醒；Java 是唯一业务入口，FastAPI 只提供内部 AI 能力。结果由 PostgreSQL 保存并缓存，不引 Redis 队列或缓存。过期结果读取时重算，定期清理留待阶段 6.5。

验收：`mvn -f apps/api/pom.xml test`; `python -m compileall apps/ai`; `pnpm -C apps/web lint`; `pnpm -C apps/web build`; `docker compose config`。人工完成真实 FastAPI + mock LLM + 独立 PostgreSQL 的端到端验证，以及权限、超时、降级和 migration 检查；禁止测试时调用付费 LLM。

## 阶段 6.5：AI 结果定期清理（backlog）

目标：按保留策略定期清理已过期 `ai_suggestions`；阶段 6 只在查询时将过期结果视为缓存未命中，不主动删除。

## 阶段 7：前端骨架

目标：搭建路由、布局、导航、认证状态、API 客户端和基础设计系统，不在阶段 6 实现页面。

## 阶段 8：前端业务页面

目标：实现线索、跟进、任务、AI 建议和统计报表页面，并覆盖加载、空、错误及权限状态。

## 阶段 9：联调 + Docker 一键启动

目标：完成前后端联调，校验 Compose 一键启动、健康检查、环境变量和本地完整业务流程。

## 阶段 10：文档 + cloc + 汇报

目标：收敛架构/API/运维文档，统计代码规模（cloc），整理验收结果与项目汇报材料。

## 每阶段的共同完成条件

代码、测试、迁移、OpenAPI 和相关文档在同一 PR 内更新；CI 通过；无未解释的 lint/type 错误；涉及 UI 必须附桌面截图；涉及权限必须附允许/拒绝用例；不得把真实客户数据用于测试或提交。
## 阶段 4.5 backlog

- Scheduler 并发集成测试。
- Testcontainers / 真实数据库 E2E。
- 完整认证链路 MockMvc E2E。
- LeadView 前端启用。

## 阶段 4.5 backlog（不阻塞阶段 5）

- timeline metadata 序列化异常：应为 JSON 对象，实际是 {"type":"jsonb","value":"..."}，需 Jackson 自定义序列化 PGobject

- tasks/today 只返回今日到期和逾期任务，不含明日到期；产品侧评估是否扩展为"未来 3 天"

- Testcontainers 真实 PostgreSQL 集成测试

- ImportJobWorker 测试期间连真实数据库的噪音，需测试 profile 关闭

- timeline cursor 分页（当前 offset）

## 阶段 3.5 pipeline backlog

- 原阶段 5 的阶段看板：恢复 pipeline stage 配置运行时 API、阶段变更交互及前端看板。该工作不属于本次阶段 5 报表范围。

## 阶段编号历史说明

本路线图早期的阶段 5 指“阶段看板与跟进时间线”，早期阶段 6 指“任务、提醒与 BullMQ”，早期阶段 7 指“仪表盘与 ECharts”，早期阶段 8～10 指导入运维、质量加固和发布验收。按 ADR 0002，原阶段 5 调整至阶段 3.5 pipeline backlog，原阶段 7 统计并入当前阶段 5。按 ADR 0003，现阶段 5.5 为金额报表 backlog、阶段 6 为 AI 智能跟进，后续阶段 7～10 调整为前端骨架、前端业务页面、联调与 Docker 一键启动、文档/cloc/汇报。历史编号保留供追溯。
## 调整后的阶段编号（ADR 0003）

- 阶段 5.5：金额报表（backlog，不实施）
- 阶段 6：AI 智能跟进（本次）
- 阶段 7：前端骨架
- 阶段 8：前端业务页面
- 阶段 9：联调 + Docker 一键启动
- 阶段 10：文档 + cloc + 汇报

历史阶段编号保留在原记录中，不追溯改写已合并阶段 3/4/5 的内容。阶段 6.5 定期清理过期 `ai_suggestions` 为 backlog。
