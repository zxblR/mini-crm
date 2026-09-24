# ADR 0003: AI 智能跟进契约

- 状态：已接受
- 日期：2026-09-24
- 范围：阶段 6，AI 智能跟进

## 决策

1. Roadmap 阶段编号调整为：阶段 5.5 金额报表（backlog，不实施）；阶段 6 AI 智能跟进（本次）；阶段 7 前端骨架；阶段 8 前端业务页面；阶段 9 联调 + Docker 一键启动；阶段 10 文档 + cloc + 汇报。历史编号保留在路线图的历史说明中。
2. AI 触发接口只允许 OWNER、ADMIN 或该线索负责人的 SALES 使用。SUPPORT 只能读取 `ai_suggestions`。跨组织访问统一返回 404。
3. Java API 同步调用 AI：连接超时 2 秒，读取超时 8 秒，总预算 10 秒；最多重试 1 次，仅覆盖连接异常、429、502、503、504。接口文档明确 P99 可能达到 10-12 秒。
4. PostgreSQL `ai_suggestions` 负责缓存，不引入 Redis。TTL 到期不主动删除，查询时视为未命中并重算；定期清理列为阶段 6.5 backlog。
5. 默认 LLM 为 DeepSeek，使用 OpenAI-compatible 协议。`LLM_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL` 均由环境变量配置。本地测试必须使用 mock LLM HTTP 服务，生产部署由人工填写 `LLM_API_KEY`。
6. 保持 `apps/ai/main.py` 的既有 `/health` 等接口兼容；新代码位于 `apps/ai/ai_service/`，`main.py` 只负责注册新路由。

## 约束

- AI 服务不访问业务数据库，也不保存业务状态。
- 客户文本作为数据字段传给模型；system prompt 明确要求忽略文本中的指令，防止 prompt 注入。
- Java 先完成认证、RBAC、组织隔离和负责人校验，才调用 AI。
- 缓存唯一键为 `organization_id + lead_id + suggestion_type + input_hash + prompt_version`。
- 数据库写入显式写入 `created_at` 和 `updated_at`；时间参数统一经 `JdbcTimeUtils.toDbTime` 转换。

## 后续

阶段 6.5 增加过期 `ai_suggestions` 的定期清理任务；本次不主动删除过期记录。
