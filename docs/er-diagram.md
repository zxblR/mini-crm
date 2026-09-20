# Mini CRM ER Diagram

以下模型以单组织 MVP 为基线。租户业务实体使用 UUID 主键、与生命周期相符的时间字段（`created_at`、`updated_at` 或事件时间，必要时 `deleted_at`），并直接保存 `organization_id`；全局角色字典 `ROLES` 除外，关联表通过复合外键保证组织一致。字段名为数据库 `snake_case`，Java 使用 JDBC Repository 访问。

运行时说明：Java Spring Boot API 位于 `apps/api`，Python FastAPI 位于 `apps/ai`；本地 Java API 使用 `8080` 端口，PostgreSQL 宿主端口统一为 `55432`。以下实体、字段、关系和约束保持不变。

```mermaid
erDiagram
    ORGANIZATIONS ||--o{ USERS : contains
    ORGANIZATIONS ||--o{ LEADS : owns
    ORGANIZATIONS ||--o{ TAGS : defines
    ORGANIZATIONS ||--o{ PIPELINE_STAGES : configures
    ORGANIZATIONS ||--o{ USER_ROLES : isolates
    ORGANIZATIONS ||--o{ REFRESH_TOKENS : isolates
    ORGANIZATIONS ||--o{ LEAD_TAGS : isolates
    ORGANIZATIONS ||--o{ FOLLOW_UPS : owns
    ORGANIZATIONS ||--o{ TASKS : owns
    ORGANIZATIONS ||--o{ STAGE_HISTORIES : owns
    ORGANIZATIONS ||--o{ IMPORT_JOBS : owns
    ORGANIZATIONS ||--o{ AUDIT_LOGS : owns
    USERS ||--o{ USER_ROLES : has
    ROLES ||--o{ USER_ROLES : grants
    USERS ||--o{ REFRESH_TOKENS : holds
    REFRESH_TOKENS o|--o| REFRESH_TOKENS : rotates_to
    USERS ||--o{ LEADS : owns
    USERS ||--o{ LEADS : creates
    USERS ||--o{ FOLLOW_UPS : creates
    USERS ||--o{ TASKS : assigned_to
    USERS ||--o{ TASKS : creates
    USERS ||--o{ STAGE_HISTORIES : changes
    USERS ||--o{ IMPORT_JOBS : starts
    USERS ||--o{ AUDIT_LOGS : performs
    LEADS }o--|| PIPELINE_STAGES : current_stage
    LEADS ||--o{ LEAD_TAGS : labeled
    TAGS ||--o{ LEAD_TAGS : labels
    LEADS ||--o{ FOLLOW_UPS : has
    LEADS ||--o{ TASKS : schedules
    LEADS ||--o{ STAGE_HISTORIES : changes
    LEADS ||--o{ AUDIT_LOGS : concerns
    PIPELINE_STAGES ||--o{ STAGE_HISTORIES : from_stage
    PIPELINE_STAGES ||--o{ STAGE_HISTORIES : to_stage

    ORGANIZATIONS {
      uuid id PK
      varchar name
      varchar timezone
      timestamptz created_at
      timestamptz updated_at
    }
    USERS {
      uuid id PK
      uuid organization_id FK
      varchar name
      varchar email
      varchar email_normalized
      varchar phone
      varchar phone_normalized
      varchar password_hash
      boolean is_active
      timestamptz last_login_at
      timestamptz created_at
      timestamptz updated_at
    }
    ROLES {
      uuid id PK
      varchar code UK
      varchar name
      timestamptz created_at
      timestamptz updated_at
    }
    USER_ROLES {
      uuid organization_id PK,FK
      uuid user_id PK,FK
      uuid role_id PK,FK
      timestamptz created_at
    }
    REFRESH_TOKENS {
      uuid id PK
      uuid organization_id FK
      uuid user_id FK
      varchar token_hash UK
      uuid replaced_by_id FK
      timestamptz expires_at
      timestamptz revoked_at
      timestamptz last_used_at
      timestamptz created_at
      timestamptz updated_at
    }
    PIPELINE_STAGES {
      uuid id PK
      uuid organization_id FK
      varchar code
      varchar name
      int sort_order
      boolean is_default
      boolean is_won
      boolean is_lost
      boolean is_active
      timestamptz created_at
      timestamptz updated_at
    }
    LEADS {
      uuid id PK
      uuid organization_id FK
      uuid owner_id FK
      uuid created_by_id FK
      uuid stage_id FK
      varchar name
      varchar company
      varchar email
      varchar email_normalized
      varchar phone
      varchar phone_normalized
      varchar source
      varchar industry
      varchar region
      text notes
      text outcome_note
      varchar lost_reason
      timestamptz closed_at
      timestamptz next_follow_up_at
      timestamptz archived_at
      timestamptz created_at
      timestamptz updated_at
    }
    TAGS {
      uuid id PK
      uuid organization_id FK
      varchar name
      varchar color
      timestamptz created_at
      timestamptz updated_at
    }
    LEAD_TAGS {
      uuid organization_id PK,FK
      uuid lead_id PK,FK
      uuid tag_id PK,FK
      timestamptz created_at
    }
    FOLLOW_UPS {
      uuid id PK
      uuid organization_id FK
      uuid lead_id FK
      uuid created_by_id FK
      varchar type
      timestamptz occurred_at
      text summary
      text result
      timestamptz next_step_at
      timestamptz created_at
      timestamptz updated_at
      timestamptz deleted_at
    }
    TASKS {
      uuid id PK
      uuid organization_id FK
      uuid lead_id FK
      uuid assignee_id FK
      uuid created_by_id FK
      varchar title
      varchar status
      timestamptz due_at
      timestamptz completed_at
      timestamptz cancelled_at
      text resolution_note
      timestamptz created_at
      timestamptz updated_at
    }
    STAGE_HISTORIES {
      uuid id PK
      uuid organization_id FK
      uuid lead_id FK
      uuid from_stage_id FK
      uuid to_stage_id FK
      uuid changed_by_id FK
      text note
      timestamptz changed_at
    }
    IMPORT_JOBS {
      uuid id PK
      uuid organization_id FK
      uuid created_by_id FK
      varchar type
      varchar status
      varchar source_filename
      varchar source_object_key
      varchar error_object_key
      int total_count
      int processed_count
      int succeeded_count
      int failed_count
      text error_summary
      timestamptz created_at
      timestamptz updated_at
      timestamptz finished_at
    }
    AUDIT_LOGS {
      uuid id PK
      uuid organization_id FK
      uuid actor_id FK
      uuid lead_id FK
      varchar action
      varchar resource_type
      varchar resource_id
      jsonb metadata
      inet ip_address
      timestamptz created_at
    }
```

## 约束与索引

- `USERS` 使用 `(organization_id, email_normalized)`、`(organization_id, phone_normalized)` 唯一约束；规范化字段可为空，写入前统一去空格并做大小写或号码格式归一化。
- `USERS` 必须至少提供 `email`、`phone` 之一；`LEADS` 必须至少提供 `name`、`company` 之一，且 `source`、`stage_id`、`created_by_id` 必填。
- `REFRESH_TOKENS.token_hash` 全局唯一，只保存不可逆哈希；`replaced_by_id` 指向轮换后的 token。同一用户的活动 token 查询索引为 `(organization_id, user_id, revoked_at, expires_at)`。
- `USER_ROLES`、`LEAD_TAGS` 使用包含 `organization_id` 的联合主键。业务关联使用 `(organization_id, id)` 复合外键，禁止把不同组织的用户、阶段、标签、线索或任务关联起来。
- `PIPELINE_STAGES` 在组织内对 `code`、`sort_order` 唯一；部分唯一索引保证每个组织至多一个活动的 `is_default=true` 阶段，服务层事务保证至少一个；`is_won` 与 `is_lost` 不得同时为真。
- `TAGS` 在组织内对规范化后的名称唯一。删除线索或标签时仅级联删除 `LEAD_TAGS`；跟进、阶段历史和审计数据不得级联物理删除。
- `LEADS` 建立 `(organization_id, owner_id, updated_at)`、`(organization_id, stage_id, updated_at)`、`(organization_id, next_follow_up_at)`、`(organization_id, archived_at)` 索引；手机号和邮箱去重使用与用户相同的规范化列及组织内部分唯一索引。
- 线索进入赢单或输单阶段时必须写入 `closed_at`、`outcome_note`；进入输单阶段还必须写入 `lost_reason`，离开终态时三者清空。阶段变更与 `STAGE_HISTORIES` 新记录在同一事务提交。
- `FOLLOW_UPS` 建立 `(organization_id, lead_id, occurred_at DESC)` 索引；删除接口只写 `deleted_at`。`TASKS` 建立 `(organization_id, assignee_id, status, due_at)` 和 `(organization_id, lead_id, created_at DESC)` 索引。
- `STAGE_HISTORIES` 建立 `(organization_id, lead_id, changed_at DESC)` 索引；`AUDIT_LOGS` 建立 `(organization_id, created_at DESC)`、`(organization_id, actor_id, created_at DESC)`、`(organization_id, resource_type, resource_id)` 索引。
- `IMPORT_JOBS` 建立 `(organization_id, created_by_id, created_at DESC)` 和 `(organization_id, status, created_at)` 索引；原文件和错误文件只存对象键，不在数据库保存 CSV 内容。
- 状态和类型使用 PostgreSQL 枚举，并由 Java 枚举映射：任务状态 `pending|completed|cancelled`，导入状态 `queued|running|completed|failed`，跟进类型 `call|wechat|email|meeting|other`。逾期不是持久化状态，而是 `status=pending AND due_at<now()` 的派生结果。
- `TASKS` 使用 CHECK 约束保证 `completed` 仅设置 `completed_at`、`cancelled` 仅设置 `cancelled_at`、`pending` 两者均为空；导入计数字段不得为负，且 `succeeded_count + failed_count <= processed_count <= total_count`。
- 阶段历史和审计日志只追加，不提供普通用户更新或删除接口；保留策略由后续合规需求另行定义。
