# Mini CRM REST API

## 1. 通用约定

- Java 业务 API 的 Base URL 为 `/api/v1`，默认本地地址为 `http://localhost:8080`。
- Content-Type 为 `application/json`；认证使用 `Authorization: Bearer <accessToken>`。
- 成功响应使用 `{ "data": ..., "meta": ..., "error": null }`；失败响应使用 `{ "data": null, "meta": ..., "error": { "code": "...", "message": "...", "details": {} } }`。
- 分页参数 `page` 从 1 开始，默认 `pageSize=20`，最大 `pageSize=100`；分页 meta 包含 `page`、`pageSize`、`total`。
- 时间使用 UTC ISO 8601；数据库时间为 `timestamptz`。Java 通过 `JdbcTimeUtils` 进行 JDBC 时间参数和结果映射。
- 资源按当前用户组织隔离；不存在、跨组织或不可见资源统一返回 `404 RESOURCE_NOT_FOUND`。
- 常见状态码：`200` 查询/更新/删除成功，`201` 创建成功，`202` 异步任务已接受，`400` 参数错误，`401` 未认证，`403` 无权限，`404` 不存在或不可见，`409` 冲突，`429` 限流，`500` 服务错误，`503` 依赖不可用。
- Swagger UI 为 `/api/docs`，OpenAPI JSON 为 `/api/docs-json`，不属于版本化业务 API。

## 2. 角色与权限

| 角色 | 说明 |
| --- | --- |
| `OWNER` | 组织最高管理角色，可管理成员、团队和全部业务数据 |
| `ADMIN` | 可管理成员、线索、任务、报表和 AI 生成 |
| `SALES` | 可创建线索，只能管理自己负责的线索、跟进和任务 |
| `SUPPORT` | 组织范围只读，可查看线索、任务、报表和已保存 AI 结果，不可写入或导出 |

角色不能替代组织归属校验。客户端传入的 `ownerId`、`assigneeId`、标签 ID 和资源 ID 都必须重新按当前 token 的组织验证。

## 3. 认证与当前用户

### `POST /auth/login`

公开接口。请求：

```json
{ "account": "admin@example.com", "password": "password123" }
```

成功 `data` 包含 `accessToken`、`refreshToken`、`expiresIn` 和用户摘要。

### `POST /auth/refresh`

公开接口。请求 `{ "refreshToken": "..." }`。旧 refresh token 轮换失效，返回新的 access/refresh token。

### `POST /auth/logout`

已认证。请求 `{ "refreshToken": "..." }`，撤销当前 refresh token。

### `GET /me` 或 `GET /auth/me`

已认证。返回：

```json
{
  "id": "uuid",
  "organizationId": "uuid",
  "name": "Demo Owner",
  "email": "admin@example.com",
  "phone": null,
  "roles": ["OWNER"],
  "permissions": ["lead:read"]
}
```

当前 Java API 同时保留 `/me` 和 `/auth/me` 的兼容入口。

## 4. 成员与团队

### `GET /users`

权限：`OWNER`、`ADMIN`。查询 `page,pageSize,keyword,isActive,role`，密码字段永不返回。

### `POST /users`

权限：`OWNER`、`ADMIN`。请求字段：`name,email,phone,password,roleCodes`。邮箱和手机号至少提供一个，组织内重复返回 `409 USER_DUPLICATE`。

### `PATCH /users/:id`

权限：`OWNER`、`ADMIN`。可更新姓名、邮箱、手机号、角色和启用状态。

### `PATCH /users/:id/status`

权限：`OWNER`、`ADMIN`。请求 `{ "isActive": true }`。停用用户会撤销其 refresh token；不能停用组织最后一个活动管理员，返回 `409 LAST_ADMIN_REQUIRED`。

### `GET /users/me` / `PATCH /users/me`

已认证。读取或更新当前用户的姓名、邮箱和手机号。

### `GET /teams/current` / `PATCH /teams/current`

已认证。读取或更新当前组织团队信息；具体可写字段以 Java DTO 校验为准。

## 5. 线索

### `GET /leads`

已认证。查询：

```text
page,pageSize,keyword,status,ownerId,source,from,to,archived,sortBy,sortOrder
```

`SALES` 只能读取自己负责的线索；查询其他组织的负责人 ID 返回 `404`，查询本组织其他负责人的范围返回 `403`。

### `POST /leads`

权限：`OWNER`、`ADMIN`、`SALES`。请求字段：

```json
{
  "name": "王敏",
  "company": "示例公司",
  "phone": "...",
  "email": "...",
  "source": "website",
  "status": "new",
  "ownerId": "uuid",
  "tagIds": ["uuid"],
  "notes": "...",
  "nextFollowUpAt": "2026-09-26T02:00:00Z"
}
```

姓名和公司至少提供一个，`source` 必填；创建时不能直接进入 `won` 或 `lost`。规范化邮箱或手机号冲突返回 `409 LEAD_DUPLICATE`。

### `GET /leads/:id`

按资源可见范围读取详情、标签、负责人和时间线。可选查询 `timelinePage,timelinePageSize`。

### `PATCH /leads/:id`

权限：组织管理员或负责人 `SALES`。可更新姓名、公司、联系方式、来源、行业、地区、备注、负责人、标签和下一次跟进时间；状态必须使用专用状态接口。

### `POST /leads/:id/archive` / `POST /leads/:id/restore`

管理员可归档和恢复；负责人 `SALES` 可归档自己负责的线索但不能恢复。`SUPPORT` 只读。

### `POST /leads/:id/assign`

权限：`OWNER`、`ADMIN`。请求 `{ "ownerId": "uuid" }`。

### `POST /leads/batch-assign`

权限：`OWNER`、`ADMIN`。请求 `{ "leadIds": ["uuid"], "ownerId": "uuid" }`，线索数量为 1 到 100，任一资源不可见时整批失败。

### `POST /leads/:id/status`

请求：

```json
{
  "status": "qualified",
  "note": "完成需求访谈",
  "outcomeNote": null,
  "lostReason": null
}
```

状态值为 `new|contacted|qualified|proposal|negotiation|won|lost`。`SALES` 只能按正向状态机推进自己的未归档线索；`OWNER`、`ADMIN` 可纠正或重新打开线索。进入终态必须填写 `outcomeNote`，进入 `lost` 必须填写 `lostReason`。

### `POST /leads/import`

权限：`OWNER`、`ADMIN`。接收 UTF-8 CSV multipart，成功返回 `202` 和 `{ "jobId": "uuid", "status": "queued" }`。

### `GET /jobs/:id`

管理员可查看组织内导入任务，其他用户只能查看本人发起的任务。状态为 `queued|running|completed|failed`。

### `GET /leads/export`

权限：`OWNER`、`ADMIN`。同步流式 CSV 导出，有固定最大行数和公式注入转义；`SUPPORT` 不可导出。

## 6. 跟进与任务

### `GET /leads/:id/follow-ups`

按线索可见范围分页读取未软删除跟进。查询 `page,pageSize,type,from,to`。

### `POST /leads/:id/follow-ups`

权限：`OWNER`、`ADMIN`、负责人 `SALES`。请求：

```json
{
  "type": "call",
  "occurredAt": "2026-09-26T02:00:00Z",
  "summary": "确认预算",
  "result": "下周给方案",
  "nextStepAt": "2026-09-30T02:00:00Z"
}
```

有 `nextStepAt` 时，服务会在同一业务流程中创建或更新关联任务。

### `GET /follow-ups/:id` / `PATCH /follow-ups/:id` / `DELETE /follow-ups/:id`

按线索可见范围读取；创建者、负责人或管理员可更新；删除为软删除并写审计日志，重复删除保持幂等。

### `GET /tasks`

查询 `page,pageSize,status,dueFrom,dueTo,assigneeId,leadId`。支持 `pending|completed|cancelled|overdue` 查询语义；销售默认只能读取自己的任务。

### `GET /tasks/today`

返回当前 UTC 自然日内的待处理任务分页。

### `PATCH /tasks/:id`

任务负责人、`OWNER`、`ADMIN` 可更新标题、到期时间和负责人；终态任务不能修改。

### `POST /tasks/:id/complete` / `POST /tasks/:id/cancel`

请求可带 `{ "note": "..." }`，分别完成或取消任务。重复终态操作返回对应冲突错误。

## 7. 标签

### `GET /tags`

已认证分页读取组织标签，查询 `page,pageSize,keyword`。

### `POST /tags` / `PATCH /tags/:id`

权限：`OWNER`、`ADMIN`。请求 `{ "name": "高意向", "color": "#E11D48" }`；组织内规范化名称唯一。

## 8. 仪表盘与审计

既有接口：

- `GET /dashboard/summary`
- `GET /dashboard/funnel`
- `GET /dashboard/sources`
- `GET /dashboard/owners`
- `GET /dashboard/recent-activities`

统计扩展接口：

- `GET /dashboard/overview`
- `GET /dashboard/channels`
- `GET /dashboard/sales-ranking`
- `GET /dashboard/trend`
- `GET /dashboard/loss-reasons`
- `GET /dashboard/response-times`

统计使用 UTC 半开区间 `[from,to)`，默认最近 30 天，最大跨度 366 天。赢单/输单使用当前 `leads.status` 和 `closed_at`；销售归属使用当前 `leads.owner_id`；响应时长使用首条未删除跟进与线索创建时间的差值，是运营代理指标，不是 SLA。

### `GET /audit-logs`

权限：`OWNER`、`ADMIN`。查询 `actorId,action,resourceType,from,to,page,pageSize`；metadata 脱敏。

### `GET /health` / `GET /ready`

公开接口。`/health` 检查进程；`/ready` 检查 PostgreSQL，依赖不可用返回 `503 DEPENDENCY_UNAVAILABLE`。

## 9. AI 内部接口

浏览器只能调用 Java API 的 AI 路由；Java 完成认证、RBAC、资源可见性和组织校验后，再调用 FastAPI。AI 路由需要请求头 `X-Organization-Id` 与当前 principal 的组织 ID 一致。

Java 路由：

- `POST /leads/:id/ai/intent-score`
- `POST /leads/:id/ai/follow-up-summary`
- `POST /leads/:id/ai/next-action`
- `POST /leads/:id/ai/script`
- `POST /leads/:id/ai/wake-up`
- `GET /leads/:id/ai/:type`

POST 请求体为 `{ "forceRefresh": false }`。触发权限为 `OWNER`、`ADMIN` 和负责人 `SALES`；`SUPPORT` 只能读取已保存结果。

Java 到 FastAPI 的内部端点为：

- `/internal/v1/intent-score`
- `/internal/v1/follow-up-summary`
- `/internal/v1/next-action`
- `/internal/v1/script`
- `/internal/v1/wake-up`

FastAPI 使用 `AI_SERVICE_TOKEN` Bearer 鉴权和严格 Pydantic 输入输出校验。Java 调用连接超时 2 秒、读取超时 8 秒、总预算 10 秒，最多重试一次；缓存 miss 且 AI 不可用时返回 `503 AI_DEPENDENCY_UNAVAILABLE`，不伪造成功结果。默认 LLM 通过 `LLM_BASE_URL` 配置，本地使用 mock LLM。

AI 结果写入 PostgreSQL `ai_suggestions`，缓存键为组织、线索、类型、`input_hash`、`prompt_version`。TTL 到期查询视为未命中并重算；定期清理属于阶段 6.5 backlog。建议不会自动修改线索或创建任务。

## 10. 最小错误码

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | DTO、分页、日期或枚举校验失败 |
| 400 | `OUTCOME_NOTE_REQUIRED` | 进入终态未填写结果说明 |
| 400 | `LOST_REASON_REQUIRED` | 进入输单未填写输单原因 |
| 401 | `AUTH_INVALID_CREDENTIALS` | 登录凭据错误 |
| 401 | `AUTH_TOKEN_EXPIRED` | access token 过期 |
| 401 | `AUTH_TOKEN_REVOKED` | refresh token 已撤销或轮换 |
| 403 | `FORBIDDEN` | 已认证但无操作权限 |
| 404 | `RESOURCE_NOT_FOUND` | 不存在、跨组织或不可见 |
| 409 | `USER_DUPLICATE` | 成员联系方式重复 |
| 409 | `LAST_ADMIN_REQUIRED` | 不能停用最后一个活动管理员 |
| 409 | `LEAD_DUPLICATE` | 线索联系方式重复 |
| 409 | `STAGE_INVALID_TRANSITION` | 状态迁移非法 |
| 409 | `TASK_TERMINAL_STATE` | 终态任务不可修改 |
| 409 | `TASK_ALREADY_COMPLETED` | 重复完成任务 |
| 409 | `TASK_ALREADY_CANCELLED` | 重复取消任务 |
| 400 | `IMPORT_INVALID_FILE` | CSV 文件无效 |
| 429 | `RATE_LIMITED` | 请求限流 |
| 500 | `INTERNAL_ERROR` | 未预期错误 |
| 503 | `DEPENDENCY_UNAVAILABLE` | PostgreSQL 等依赖不可用 |
| 503 | `AI_DEPENDENCY_UNAVAILABLE` | AI 服务不可用且无有效缓存 |

## 11. 契约风险记录

当前 Java AI 运行时和 `20260924000000_ai_suggestions` migration 使用 `created_by`、`suggestion_type` 字段；`prisma/schema.prisma` 末尾仍存在重复的 AI 声明，字段名和类型与 migration 不完全一致。阶段 10 不修改 schema/migration，下一次数据库相关变更前必须先完成 schema 清理和 `prisma validate`。
