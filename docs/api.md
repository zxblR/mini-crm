# Mini CRM REST API

业务 API 由 Java 21 + Spring Boot 提供；AI 专用接口由 Python FastAPI 内部服务提供。前端只访问 Java API，不直接访问 AI 服务。

## 1. 通用约定

- 本地运行地址：Java API 为 `http://localhost:8080`；PostgreSQL 宿主端口为 `55432`。容器间调用使用 Compose 服务名，不依赖宿主端口。
- Base URL：`/api/v1`；Content-Type：`application/json`；认证：`Authorization: Bearer <accessToken>`。
- 所有成功响应均返回 JSON：`{ "data": ..., "meta": { "requestId": "..." }, "error": null }`；不使用空响应，删除、退出等操作也返回该结构。
- 失败响应：`{ "data": null, "meta": { "requestId": "..." }, "error": { "code": "...", "message": "...", "details": {} } }`。
- 分页参数：`page` 从 1 开始，`pageSize` 默认 20、最大 100；响应 `meta` 包含 `page`、`pageSize`、`total`。
- 时间使用 UTC ISO 8601；所有资源按当前用户组织隔离。不存在或无权限的资源统一返回 `404 RESOURCE_NOT_FOUND`，避免泄露资源存在性。
- 常见状态码：`200` 查询、更新、删除或退出成功，`201` 创建成功，`202` 异步任务已接受，`400` 参数错误，`401` 未认证，`403` 无权限，`404` 不存在，`409` 冲突，`429` 限流，`500` 服务错误，`503` 依赖未就绪。
- 下文“响应：某对象/列表”描述的是 `data` 字段；实际响应始终包含完整的 `data`、`meta`、`error` 三个顶层字段。

## 2. 权限代码

`OWNER`：当前组织的最高管理角色，可管理组织、成员和全部业务数据；`ADMIN`：可管理成员、全部线索和报表；`SALES`：仅管理自己负责的线索、跟进和任务，可创建线索；`SUPPORT`：当前组织业务数据只读，不可导入或导出。后台任务是内部服务组件，不作为用户角色，也不签发用户 access token。MVP 不启用销售共享池；后续若开放必须新增权限代码和 ADR。除登录、刷新、健康检查和就绪检查外均需认证。

角色不能替代组织归属校验。客户端提供的 `organizationId`、`ownerId`、`assigneeId` 等标识必须按当前 token 的组织重新校验；跨组织访问统一返回 `404 RESOURCE_NOT_FOUND`。

## 3. 认证与用户

### `POST /auth/login`

权限：公开。请求：`{ "account": "alice@example.com", "password": "..." }`。响应 `200` 的 `data`：`{ "accessToken": "...", "refreshToken": "...", "expiresIn": 900, "user": { "id": "uuid", "name": "Alice", "roles": ["sales"] } }`。

### `POST /auth/refresh`

权限：公开但需有效 refresh token。请求：`{ "refreshToken": "..." }`。响应：新的 access/refresh token；旧 refresh token 立即轮换失效。

### `POST /auth/logout`

权限：已认证。请求：`{ "refreshToken": "..." }`。响应 `200`：`{ "data": { "loggedOut": true }, "meta": { "requestId": "..." }, "error": null }`，撤销当前 refresh token。

### `GET /me`

权限：已认证。响应的 `data`：`{ "id": "uuid", "organizationId": "uuid", "name": "Alice", "email": "...", "roles": ["sales"], "permissions": ["lead:read"] }`。

### `GET /users`

权限：`admin`。查询：`page,pageSize,keyword,isActive,role`。响应：用户分页列表，密码字段永不返回。

### `POST /users`

权限：`admin`。请求：`{ "name": "Alice", "email": "alice@example.com", "phone": null, "password": "...", "roleCodes": ["sales"] }`。`email`、`phone` 至少提供一个，密码不得写入日志。响应 `201`：新建用户摘要；组织内规范化邮箱或手机号重复时返回 `409 USER_DUPLICATE`。

### `PATCH /users/:id`

权限：`admin`。请求示例：`{ "name": "Alice", "roleCodes": ["sales"], "isActive": true }`。响应：更新后的用户摘要。

停用用户时撤销其全部 refresh token；禁止管理员停用当前组织最后一个活动管理员，冲突时返回 `409 LAST_ADMIN_REQUIRED`。

## 4. 线索与固定状态

阶段 3 使用固定 `LeadStatus`：`new|contacted|qualified|proposal|negotiation|won|lost`。可配置 pipeline 不属于阶段 3，对应数据库结构仅作为阶段 3.5 预留，不提供运行时 API。

标准正向路径为 `new -> contacted -> qualified -> proposal -> negotiation -> won|lost`。`SALES` 只能推进自己负责且未归档的线索到下一状态；`OWNER`、`ADMIN` 可将未归档线索纠正到任意不同状态，也可将终态重新打开；`SUPPORT` 不可变更状态。

### `GET /leads`

权限：`OWNER`、`ADMIN`、`SUPPORT` 可读取当前组织全部线索；`SALES` 只能读取 `ownerId` 为自己的线索。查询：`page,pageSize,keyword,status,ownerId,source,from,to,archived,sortBy,sortOrder`；`SALES` 传入当前组织内其他 `ownerId` 返回 `403 FORBIDDEN`，传入跨组织 ID 返回 `404 RESOURCE_NOT_FOUND`。响应：

```json
{
  "data": [{ "id": "uuid", "name": "王敏", "company": "示例公司", "ownerId": "uuid", "status": "contacted", "nextFollowUpAt": "2026-09-20T02:00:00Z", "updatedAt": "2026-09-18T03:00:00Z" }],
  "meta": { "page": 1, "pageSize": 20, "total": 1 },
  "error": null
}
```

### `POST /leads`

权限：`OWNER`、`ADMIN`、`SALES`。请求：`{ "name": "王敏", "company": "示例公司", "phone": "...", "email": "...", "source": "website", "status": "new", "ownerId": "uuid", "tagIds": ["uuid"], "notes": "..." }`。`name`、`company` 至少一个非空，`source` 必填；`status` 省略时默认为 `new`，创建时不允许直接进入 `won|lost`；`SALES` 只能省略 `ownerId` 或指定自己。响应 `201`：完整线索摘要。重复手机号/邮箱时返回 `409 LEAD_DUPLICATE`，`error.details.candidateLeadIds` 只提供当前用户有权查看的候选 ID。

邮箱规范化字段固定为数据库列 `normalized_email`，规则为去除首尾空白后使用 `Locale.ROOT` 小写；手机规范化字段固定为 `normalized_phone`，规则为去除空白及常见分隔符并保留一个合法的前导 `+`。同组织内任一规范化值命中即判定重复，归档线索仍参与去重。

### `GET /leads/:id`

权限：按线索可见范围。查询可选 `timelinePage,timelinePageSize`（从 1 开始，默认 20，最大 100）。响应：线索详情、标签、当前阶段、负责人、`timeline`（阶段变更/跟进/任务）。Timeline 合并在本接口中，不新增 `/leads/:id/timeline`。

### `PATCH /leads/:id`

权限：`OWNER`、`ADMIN`；`SALES` 仅限负责人。请求只允许修改白名单字段：`name,company,phone,email,source,industry,region,notes,ownerId,tagIds,nextFollowUpAt`；`SALES` 不得修改 `ownerId`，状态只能通过专用状态迁移接口修改。响应：更新后的详情摘要。

### `POST /leads/:id/archive` / `POST /leads/:id/restore`

权限：`OWNER`、`ADMIN`；负责人 `SALES` 可归档自己负责的线索（恢复仅 `OWNER`、`ADMIN`）。`SUPPORT` 只读。响应：更新后的 `archivedAt`。

### `POST /leads/:id/assign`

权限：`OWNER`、`ADMIN`。请求：`{ "ownerId": "uuid" }`。响应：线索摘要；写入审计日志。

### `POST /leads/batch-assign`

权限：`OWNER`、`ADMIN`。请求：`{ "leadIds": ["uuid"], "ownerId": "uuid" }`，`leadIds` 为 1～100 个且不可重复。响应：`{ "updated": 2, "leadIds": ["uuid"] }`；任一线索不存在、跨组织或不可分配时整批回滚。

### `POST /leads/import`

权限：`OWNER`、`ADMIN`。请求：`multipart/form-data`，字段 `file`（CSV，UTF-8，最大 10MB）。响应 `202` 的 `data`：`{ "jobId": "uuid", "status": "queued" }`。通过 `GET /jobs/:id` 查询结果，不在请求线程执行大批量导入。任务由 `import_jobs` 持久化，Worker 使用 `FOR UPDATE SKIP LOCKED` 认领，服务重启后可恢复未完成任务。CSV 使用 Apache Commons CSV 解析。

### `GET /jobs/:id`

权限：`OWNER`、`ADMIN` 可查看当前组织全部导入任务；其他已认证用户只能查看本人发起的任务。响应的 `data`：`{ "id": "uuid", "type": "lead_import", "status": "queued|running|completed|failed", "processed": 0, "succeeded": 0, "failed": 0, "errorFileUrl": null, "createdAt": "2026-09-18T03:00:00Z", "finishedAt": null }`。错误明细不得包含密码、token 或未脱敏个人信息。

### `GET /leads/export`

权限：仅 `OWNER`、`ADMIN`；`SUPPORT` 明确不可导出。查询参数与 `GET /leads` 相同，但不接收分页参数；服务端设置固定最大导出行数，超过上限返回 `400 VALIDATION_FAILED`。成功返回 `text/csv; charset=UTF-8` 流，文件名由服务端生成安全 ASCII 名称。所有单元格按 CSV 规则编码；去除前导空白后以 `=,+,-,@` 开头的值必须进行公式注入转义。导出失败仍使用 JSON 错误包络。

### `POST /leads/:id/status`

请求：`{ "status": "qualified", "note": "完成需求访谈", "outcomeNote": null, "lostReason": null }`。响应：更新后的当前状态和新增状态历史。

- `SALES` 只能操作自己负责且未归档的线索，并按标准正向路径逐级推进。
- `OWNER`、`ADMIN` 可将未归档线索调整到任意不同状态，并可从 `won|lost` 重新打开。
- `OWNER`、`ADMIN` 重开终态线索时必须指定非终态目标（`new|contacted|qualified|proposal|negotiation`）；省略目标时默认 `negotiation`。目标为 `won|lost` 返回 `409 STAGE_INVALID_TRANSITION`。
- `SUPPORT` 不可变更状态。
- 进入 `won|lost` 时 `outcomeNote` 必填；进入 `lost` 时 `lostReason` 也必填。
- 重复状态、归档线索迁移或违反角色迁移矩阵时返回 `409 STAGE_INVALID_TRANSITION`。
- 状态更新、历史记录和操作日志在同一事务提交。


## 5. 跟进、任务与标签

### `GET /leads/:id/follow-ups`

权限：按线索可见范围。跟进创建和列表沿用嵌套路由 `/api/v1/leads/{leadId}/follow-ups`；查询：`page,pageSize,type,from,to`。响应：跟进记录分页。

### `POST /leads/:id/follow-ups`

权限：`OWNER`、`ADMIN`、负责人 `SALES`。请求：`{ "type": "call", "occurredAt": "2026-09-18T03:00:00Z", "summary": "确认预算", "result": "下周给方案", "nextStepAt": "2026-09-22T02:00:00Z" }`。响应 `201`：跟进记录；若有 `nextStepAt`，同时创建/更新任务。

### `GET /follow-ups/:id`

权限：按所属线索可见范围。响应：未删除的跟进记录详情。

### `PATCH /follow-ups/:id`

权限：创建者、负责人或 `OWNER`、`ADMIN`。请求：可更新摘要、结果、下一步时间；发生时间不可晚于当前时间 24 小时以上，除非 `OWNER`、`ADMIN`。

### `DELETE /follow-ups/:id`

权限：创建者或 `OWNER`、`ADMIN`。执行软删除并写入审计日志。响应 `200`：`{ "deleted": true, "id": "uuid" }`；已删除记录重复删除保持幂等。

### `GET /tasks`

权限：已认证；`OWNER`、`ADMIN`、`SUPPORT` 可读取当前组织任务，`SALES` 只能读取本人任务。查询：`status,dueFrom,dueTo,assigneeId,leadId,page,pageSize`；`SALES` 传入当前组织内其他 `assigneeId` 返回 `403 FORBIDDEN`，传入跨组织 ID 返回 `404 RESOURCE_NOT_FOUND`。响应：任务分页。

### `GET /tasks/today`

权限：同 `GET /tasks`；返回当前 UTC 自然日内的 pending 任务分页，支持 `page,pageSize`。

### `PATCH /tasks/:id`

权限：任务负责人、`OWNER`、`ADMIN`。请求仅允许修改 `title,dueAt,assigneeId`；任务负责人不能把任务改派给他人。已完成或已取消任务不可改期，返回 `409 TASK_TERMINAL_STATE`。响应：更新后的任务详情。

### `POST /tasks/:id/complete` / `POST /tasks/:id/cancel`

权限：任务负责人、`OWNER`、`ADMIN`。完成沿用 `POST /tasks/:id/complete`，不提供 `PATCH /tasks/:id/done`；取消沿用 `POST /tasks/:id/cancel`。请求可带 `{ "note": "..." }`。响应：任务状态、`completedAt` 或 `cancelledAt` 以及 `resolutionNote`；重复执行返回 `409 TASK_ALREADY_COMPLETED` 或 `409 TASK_ALREADY_CANCELLED`。

### `GET /tags` / `POST /tags` / `PATCH /tags/:id`

权限：读取需认证；写入仅 `OWNER`、`ADMIN`。`GET /tags` 查询为 `page,pageSize,keyword` 并返回分页列表；写请求为 `{ "name": "高意向", "color": "#E11D48" }`，规范化名称在组织内唯一。

## 6. 仪表盘与审计

### `GET /dashboard/summary`

权限：`OWNER`、`ADMIN`、`SUPPORT` 可查看当前组织范围；`SALES` 仅自己的范围。查询：`from,to,ownerId,source`；`SALES` 传入其他 `ownerId` 返回 `403 FORBIDDEN`。响应的 `data`：`{ "totalLeads": 120, "openLeads": 86, "wonLeads": 12, "lostLeads": 22, "overdueTasks": 7, "conversionRate": 0.1 }`。

### `GET /dashboard/funnel`

权限：同 summary。查询：`from,to,ownerId,source,page,pageSize`。响应：固定状态统计分页列表，元素为 `{ "status": "contacted", "statusName": "已联系", "count": 30 }`，并在 `meta` 中包含统计范围。

### `GET /dashboard/sources`

权限：同 summary。查询：`from,to,ownerId,page,pageSize`。响应：来源、数量、赢单数和转化率分页列表。

### `GET /dashboard/owners`

权限：同 summary；`SALES` 只能返回本人。查询：`from,to,source,page,pageSize`。响应：负责人维度的线索数、赢单数、逾期任务数和转化率分页列表。

### `GET /dashboard/recent-activities`

权限：同 summary。查询：`from,to,ownerId,page,pageSize`。响应：当前可见范围内的阶段变更、跟进和任务事件分页列表。

### 阶段 5 统计报表（兼容扩展）

新统计路由 `/dashboard/overview`、`/dashboard/channels`、`/dashboard/sales-ranking`、`/dashboard/trend`、`/dashboard/loss-reasons`、`/dashboard/response-times` 的时间范围参数为 UTC ISO-8601 offset timestamp，按半开区间 `[from,to)` 过滤；省略时默认最近 30 天，最大跨度 366 天。`from` 必须早于 `to`。`ownerId` 为可选个人范围，`SALES` 强制限定为本人，指定其他成员返回 `403 FORBIDDEN`；跨组织成员 ID 返回 `404 RESOURCE_NOT_FOUND`。`OWNER`、`ADMIN`、`SUPPORT` 默认查看当前组织，且可由 `ownerId` 筛选。统计接口均为只读，`SUPPORT` 可读但无任何写入或导出权限。分页沿用现有 `PageSupport` 和 `meta.page/pageSize/total`，默认 page 1、pageSize 20、最大 100；趋势最多返回 366 个 UTC bucket。既有 dashboard 路由继续按原有可选时间范围语义工作。

所有统计 SQL 强制 `organization_id = 当前 token 组织`。赢单/输单统一使用当前 `leads.status` 与 `closed_at`；legacy `won_at`、`lost_at` 不参与。销售赢单归属当前 `leads.owner_id`，不使用 `stage_history.actor_id`。响应时间是代理指标：`max(0, 首条未删除 follow_ups.occurred_at - leads.created_at)`，其中首条跟进按 `occurred_at` 最早选取。响应时长聚合必须返回 `respondedCount`、`unrespondedCount`、`avgResponseSeconds`（仅已响应样本平均）；分桶单独提供 `unresponded`，不混入 `>7d`。所有分桶按 UTC 计算。

| 接口 | 查询参数 | data |
| --- | --- | --- |
| `GET /dashboard/summary`（保留原字段） | `from,to,ownerId,source` | 既有响应字段和兼容行为不变；其中赢单/输单已按当前 `status + closed_at` 解释，不按 pipeline stage 标记。 |
| `GET /dashboard/sources`（保留） | `from,to,ownerId,page,pageSize` | 每项 `{ source, count, won, conversionRate }`；转化率 = 新增 cohort 中当前赢单且 `closed_at` 非空的线索数 / 此来源新增线索数。 |
| `GET /dashboard/owners`（保留） | `from,to,source,page,pageSize` | 保留既有 `{ ownerId, ownerName, total, won, overdueTasks, conversionRate }` 字段，赢单按当前 `owner_id` 归属。 |
| `GET /dashboard/sales-ranking`（新增） | `from,to,ownerId,page,pageSize` | 每项 `{ ownerId, ownerName, followUpCount, wonCount, avgDealCycleSeconds, conversionRate }`；无负责人线索不进入排名。`followUpCount` 仅统计该负责人新增 cohort 线索在范围内发生的未删除跟进；成交与周期按新增 cohort 当前状态及当前负责人。分页 meta 沿用现有格式。 |
| `GET /dashboard/trend`（新增） | `from,to,granularity=day\|week\|month,ownerId` | 每项 `{ periodStart, leadCount, wonCount }`，`periodStart` 为 UTC bucket 起点。线索按 `created_at`，赢单按当前 `status='won' AND closed_at IS NOT NULL` 的 `closed_at` 统计。返回区间内完整零值桶，最多 366 个点。 |
| `GET /dashboard/loss-reasons`（新增） | `from,to,ownerId,page,pageSize` | 每项 `{ lostReason, count }`；按 `closed_at` 过滤，当前 status 必须为 lost；空/空白原因显示 `unspecified`。分页 meta 沿用现有格式。 |
| `GET /dashboard/response-times`（新增） | `from,to,ownerId` | `{ respondedCount, unrespondedCount, avgResponseSeconds, buckets }`。桶固定为 `<1h`、`1h-24h`、`1d-7d`、`>7d`、`unresponded`；每桶含 count。平均时长只包含有首条有效跟进的线索。 |

`GET /dashboard/overview` 新增总览 DTO：`{ totalLeads, newLeads, statusCounts, conversionRate, avgDealCycleSeconds, respondedCount, unrespondedCount, avgResponseSeconds }`。`totalLeads/statusCounts` 为当前非归档线索存量；`newLeads` 为 `[from,to)` 创建的数量；`conversionRate` = 此新增 cohort 中当前 `status=won AND closed_at IS NOT NULL` 数 / 新增数量；`avgDealCycleSeconds` = 该 cohort 当前赢单线索中 `closed_at >= created_at` 样本的 `AVG(closed_at - created_at)` 秒数。`closed_at < created_at` 的记录视为脏数据，不计入 `avgDealCycleSeconds` 样本，但仍计入 `wonCount`。空分母返回 0，空样本平均值返回 null。为兼容已有调用，不更改旧 `/dashboard/summary` 的响应形状。

渠道转化率 = 同一新增 cohort 中当前赢单数 / 该来源新增数；销售转化率 = 该 owner 的新增 cohort 当前赢单数 / 其新增线索数；销售成交周期为该 cohort 当前赢单线索的平均 `closed_at - created_at` 秒数。响应时长均以秒为单位，空样本的 `avgResponseSeconds` 为 `null`。

新增响应 DTO 的 JSON 字段均为 camelCase。新增概念类型为 `StatsOverviewDTO`、`ChannelStatsDTO`、`SalesRankingDTO`、`TrendPointDTO`、`ResponseTimeStatsDTO`、`LossReasonStatsDTO`、`Granularity`。所有 success response 仍使用 `{data,meta,error}`，分页列表沿用已有 `meta` 字段，不建立独立 meta 格式。

阶段 5 仅数量和时间，不提供成交额、客单价、ROI、渠道成本、AI 分析或导出端点。当前只读统计不写操作日志；若后续提供导出，记录 `EXPORT`，metadata 只能含报表标识、UTC 时间范围、粒度和行数，不得含 PII。

### `GET /audit-logs`

权限：`OWNER`、`ADMIN`。查询：`actorId,action,resourceType,from,to,page,pageSize`。响应：只读审计分页；`metadata` 已脱敏。

### `GET /health` / `GET /ready`

权限：公开（可由编排系统调用）。`/health` 检查进程，`/ready` 检查 PostgreSQL、Redis 和队列依赖；响应同样使用统一包络。依赖不可用时返回 `503 DEPENDENCY_UNAVAILABLE`。

## 7. AI 内部接口

Java API 通过服务端网络调用 AI 服务，浏览器不得直接调用 `AI_SERVICE_URL`。

### `POST /v1/follow-up-suggestions`

服务：Python AI；当前内部开发接口。请求：`{ "lead_name": "王敏", "context": "已确认下周提供方案" }`。响应：`{ "suggestion": "...", "service": "ai-python" }`。生产环境必须由 Java API 负责用户认证、组织归属校验、超时、重试和敏感信息脱敏。

## 8. 枚举与字段约束

- 角色：`OWNER|ADMIN|SALES|SUPPORT`；`SUPPORT` 为只读角色且不可导出。后台 Worker 不属于用户角色。
- 线索状态：`new|contacted|qualified|proposal|negotiation|won|lost`。
- 跟进类型：`call|wechat|email|meeting|other`。
- 任务持久化状态：`pending|completed|cancelled`；查询参数 `status=overdue` 表示 `pending` 且 `dueAt` 早于当前时间。
- 导入任务类型：`lead_import`；状态：`queued|running|completed|failed`。
- `sortOrder`：`asc|desc`；`sortBy` 仅允许服务端白名单字段，禁止直接映射为 SQL 片段。
- 所有 ID 使用 UUID；分页、数组长度、字符串长度、邮箱、手机号、颜色和日期格式必须由共享 DTO 校验。未知字段由全局 ValidationPipe 拒绝。

## 9. 错误码最小集合

| HTTP | 错误码 | 使用场景 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | DTO、分页、日期范围或枚举校验失败 |
| 400 | `LOST_REASON_REQUIRED` | 进入输单阶段但未提供输单原因 |
| 400 | `OUTCOME_NOTE_REQUIRED` | 进入赢单或输单阶段但未提供结果说明 |
| 401 | `AUTH_INVALID_CREDENTIALS` | 登录凭据错误 |
| 401 | `AUTH_TOKEN_EXPIRED` | access/refresh token 已过期 |
| 401 | `AUTH_TOKEN_REVOKED` | refresh token 已撤销或已轮换 |
| 403 | `FORBIDDEN` | 已认证但缺少操作权限；跨组织资源仍使用 404 |
| 404 | `RESOURCE_NOT_FOUND` | 资源不存在、不可见或不属于当前组织 |
| 409 | `USER_DUPLICATE` | 组织内邮箱或手机号重复 |
| 409 | `LAST_ADMIN_REQUIRED` | 尝试停用最后一个活动管理员 |
| 409 | `LEAD_DUPLICATE` | 组织内线索手机号或邮箱重复 |
| 409 | `STAGE_INVALID_TRANSITION` | 阶段转换不符合业务规则 |
| 409 | `TASK_TERMINAL_STATE` | 尝试修改已完成或已取消任务 |
| 409 | `TASK_ALREADY_COMPLETED` | 重复完成任务 |
| 409 | `TASK_ALREADY_CANCELLED` | 重复取消任务 |
| 400 | `IMPORT_INVALID_FILE` | CSV 类型、编码、表头、大小或内容无效 |
| 429 | `RATE_LIMITED` | 请求超过限流策略 |
| 500 | `INTERNAL_ERROR` | 未预期服务错误，不返回内部堆栈 |
| 503 | `DEPENDENCY_UNAVAILABLE` | 就绪检查发现 PostgreSQL、Redis 或队列不可用 |

权限和资源错误的统一语义：

- `403 FORBIDDEN`：资源属于当前组织且调用者已知该操作，但其角色不允许执行，例如 `SALES` 查询其他成员的 `ownerId`，或 `SUPPORT` 请求导出。
- `404 RESOURCE_NOT_FOUND`：资源不存在、属于其他组织，或不在调用者的资源可见范围内。跨组织和跨负责人直接按资源 ID 访问时使用该错误，避免泄露存在性。
- `409 LEAD_DUPLICATE`：同组织规范化邮箱或手机号冲突；`candidateLeadIds` 只能包含调用者可见的候选。
- `409 STAGE_INVALID_TRANSITION`：固定 `LeadStatus` 的迁移不在角色对应矩阵内，或线索当前不可迁移。
