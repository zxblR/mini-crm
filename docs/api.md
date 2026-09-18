# Mini CRM REST API

## 1. 通用约定

- Base URL：`/api/v1`；Content-Type：`application/json`；认证：`Authorization: Bearer <accessToken>`。
- 成功响应：`{ "data": ..., "meta": { ... }, "error": null }`。
- 失败响应：`{ "data": null, "meta": { "requestId": "..." }, "error": { "code": "...", "message": "...", "details": {} } }`。
- 分页参数：`page` 从 1 开始，`pageSize` 默认 20、最大 100；响应 `meta` 包含 `page`、`pageSize`、`total`。
- 时间使用 UTC ISO 8601；所有资源按当前用户组织隔离。不存在或无权限的资源统一返回 `404 RESOURCE_NOT_FOUND`，避免泄露资源存在性。
- 常见状态码：`200` 查询/更新成功，`201` 创建成功，`204` 删除/退出成功，`400` 参数错误，`401` 未认证，`403` 无权限，`404` 不存在，`409` 冲突，`429` 限流，`500` 服务错误。

## 2. 权限代码

`admin`：组织配置、用户管理、所有线索和报表；`sales`：自己负责的线索、跟进和任务，可创建线索；`viewer`：授权范围内只读；`worker`：仅内部队列，不暴露给用户。除登录、刷新和健康检查外均需认证。

## 3. 认证与用户

### `POST /auth/login`

权限：公开。请求：`{ "account": "alice@example.com", "password": "..." }`。响应 `200`：`{ "data": { "accessToken": "...", "refreshToken": "...", "expiresIn": 900, "user": { "id": "uuid", "name": "Alice", "roles": ["sales"] } } }`。

### `POST /auth/refresh`

权限：公开但需有效 refresh token。请求：`{ "refreshToken": "..." }`。响应：新的 access/refresh token；旧 refresh token 立即轮换失效。

### `POST /auth/logout`

权限：已认证。请求：`{ "refreshToken": "..." }`。响应：`204`，撤销当前 refresh token。

### `GET /me`

权限：已认证。响应：`{ "data": { "id": "uuid", "organizationId": "uuid", "name": "Alice", "email": "...", "roles": ["sales"], "permissions": ["lead:read"] } }`。

### `GET /users`

权限：`admin`。查询：`page,pageSize,keyword,isActive,role`。响应：用户分页列表，密码字段永不返回。

### `PATCH /users/:id`

权限：`admin`。请求示例：`{ "name": "Alice", "roleCodes": ["sales"], "isActive": true }`。响应：更新后的用户摘要。

## 4. 线索与阶段

### `GET /leads`

权限：`admin` 全部；`sales` 自己负责/策略允许的共享线索；`viewer` 只读授权范围。查询：`page,pageSize,keyword,stageId,ownerId,source,from,to,archived,sortBy,sortOrder`。响应：

```json
{
  "data": [{ "id": "uuid", "name": "王敏", "company": "示例公司", "ownerId": "uuid", "stage": { "id": "uuid", "code": "contacted", "name": "已联系" }, "nextFollowUpAt": "2026-09-20T02:00:00Z", "updatedAt": "2026-09-18T03:00:00Z" }],
  "meta": { "page": 1, "pageSize": 20, "total": 1 },
  "error": null
}
```

### `POST /leads`

权限：`admin`、`sales`。请求：`{ "name": "王敏", "company": "示例公司", "phone": "...", "email": "...", "source": "website", "stageId": "uuid", "ownerId": "uuid", "tagIds": ["uuid"], "notes": "..." }`。响应 `201`：完整线索摘要。重复手机号/邮箱时返回 `409 LEAD_DUPLICATE` 并附候选线索 ID。

### `GET /leads/:id`

权限：按线索可见范围。响应：线索详情、标签、当前阶段、负责人、`timeline`（阶段变更/跟进/任务）。

### `PATCH /leads/:id`

权限：`admin`；`sales` 仅负责人或被授权共享线索。请求只允许修改白名单字段：`name,company,phone,email,source,industry,region,notes,ownerId,tagIds,nextFollowUpAt`。响应：更新后的详情摘要。

### `POST /leads/:id/archive` / `POST /leads/:id/restore`

权限：`admin`；负责人可归档自己负责的线索（恢复仅 `admin`）。响应：更新后的 `archivedAt`。

### `POST /leads/:id/assign`

权限：`admin`。请求：`{ "ownerId": "uuid" }`。响应：线索摘要；写入审计日志。

### `POST /leads/import`

权限：`admin`。请求：`multipart/form-data`，字段 `file`（CSV，UTF-8）。响应 `202`：`{ "data": { "jobId": "uuid", "status": "queued" } }`。通过 `GET /jobs/:id` 查询结果，不在请求线程执行大批量导入。

### `GET /jobs/:id`

权限：已认证，且只能查看当前组织由本人发起或被授权的任务；`admin` 可查看组织内全部导入任务。响应：`{ "data": { "id": "uuid", "type": "lead_import", "status": "queued|running|completed|failed", "processed": 0, "succeeded": 0, "failed": 0, "errorFileUrl": null, "createdAt": "2026-09-18T03:00:00Z", "finishedAt": null } }`。错误明细不得包含密码、token 或未脱敏个人信息。

### `GET /pipeline-stages`

权限：已认证。响应：当前组织活动阶段按 `sortOrder` 排列。

### `PATCH /pipeline-stages/:id`

权限：`admin`。请求：`{ "name": "需求确认", "sortOrder": 3, "isActive": true }`。已被使用的阶段不可物理删除。

### `POST /leads/:id/stage`

权限：`admin`；负责人 `sales`。请求：`{ "stageId": "uuid", "note": "完成需求访谈" , "lostReason": null }`。进入输单阶段时 `lostReason` 必填。响应：线索当前阶段和新增历史记录。

## 5. 跟进、任务与标签

### `GET /leads/:id/follow-ups`

权限：按线索可见范围。查询：`page,pageSize,type,from,to`。响应：跟进记录分页。

### `POST /leads/:id/follow-ups`

权限：`admin`、负责人 `sales`。请求：`{ "type": "call", "occurredAt": "2026-09-18T03:00:00Z", "summary": "确认预算", "result": "下周给方案", "nextStepAt": "2026-09-22T02:00:00Z" }`。响应 `201`：跟进记录；若有 `nextStepAt`，同时创建/更新任务。

### `PATCH /follow-ups/:id`

权限：创建者、负责人或 `admin`。请求：可更新摘要、结果、下一步时间；发生时间不可晚于当前时间 24 小时以上，除非 `admin`。

### `GET /tasks`

权限：已认证；`admin` 可按成员查看，`sales` 默认本人，`viewer` 只读。查询：`status,dueFrom,dueTo,assigneeId,leadId,page,pageSize`。响应：任务分页。

### `POST /tasks/:id/complete` / `POST /tasks/:id/cancel`

权限：任务负责人、`admin`。请求可带 `{ "note": "..." }`。响应：任务状态、完成/取消时间。

### `GET /tags` / `POST /tags` / `PATCH /tags/:id`

权限：读取需认证；写入仅 `admin`。请求：`{ "name": "高意向", "color": "#E11D48" }`；名称在组织内唯一。

## 6. 仪表盘与审计

### `GET /dashboard/summary`

权限：`admin`、`viewer`；`sales` 仅自己的范围。查询：`from,to,ownerId,source`。响应：`{ "data": { "totalLeads": 120, "openLeads": 86, "wonLeads": 12, "lostLeads": 22, "overdueTasks": 7, "conversionRate": 0.1 } }`。

### `GET /dashboard/funnel`

权限：同 summary。响应：`[{ "stageId": "uuid", "stageName": "已联系", "count": 30 }]`，包含统计范围。

### `GET /dashboard/sources`

权限：同 summary。响应：来源及数量、赢单数和转化率数组。

### `GET /audit-logs`

权限：`admin`。查询：`actorId,action,resourceType,from,to,page,pageSize`。响应：只读审计分页；`metadata` 已脱敏。

### `GET /health` / `GET /ready`

权限：公开（可由编排系统调用）。`/health` 检查进程，`/ready` 检查 PostgreSQL、Redis 和队列依赖；依赖不可用时返回 `503`。

## 7. 错误码最小集合

`AUTH_INVALID_CREDENTIALS`、`AUTH_TOKEN_EXPIRED`、`AUTH_TOKEN_REVOKED`、`FORBIDDEN`、`VALIDATION_FAILED`、`RESOURCE_NOT_FOUND`、`LEAD_DUPLICATE`、`STAGE_INVALID_TRANSITION`、`LOST_REASON_REQUIRED`、`TASK_ALREADY_COMPLETED`、`IMPORT_INVALID_FILE`、`RATE_LIMITED`、`INTERNAL_ERROR`。
