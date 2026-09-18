# Mini CRM ER Diagram

以下模型以单组织 MVP 为基线。所有业务表均使用 UUID 主键、`created_at`/`updated_at`（必要时 `deleted_at`）并通过 `organization_id` 做组织隔离。字段名为数据库 `snake_case`；Prisma 模型名可使用 PascalCase。

```mermaid
erDiagram
    ORGANIZATIONS ||--o{ USERS : contains
    ORGANIZATIONS ||--o{ LEADS : owns
    ORGANIZATIONS ||--o{ TAGS : defines
    ORGANIZATIONS ||--o{ PIPELINE_STAGES : configures
    USERS ||--o{ USER_ROLES : has
    ROLES ||--o{ USER_ROLES : grants
    USERS ||--o{ LEADS : owns
    USERS ||--o{ FOLLOW_UPS : creates
    USERS ||--o{ TASKS : assigned_to
    USERS ||--o{ AUDIT_LOGS : performs
    LEADS }o--|| PIPELINE_STAGES : current_stage
    LEADS }o--o| USERS : assignee
    LEADS ||--o{ LEAD_TAGS : labeled
    TAGS ||--o{ LEAD_TAGS : labels
    LEADS ||--o{ FOLLOW_UPS : has
    LEADS ||--o{ TASKS : schedules
    LEADS ||--o{ STAGE_HISTORIES : changes
    LEADS ||--o{ AUDIT_LOGS : concerns

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
      varchar email UK
      varchar phone UK
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
    }
    USER_ROLES {
      uuid user_id PK,FK
      uuid role_id PK,FK
    }
    PIPELINE_STAGES {
      uuid id PK
      uuid organization_id FK
      varchar code
      varchar name
      int sort_order
      boolean is_won
      boolean is_lost
      boolean is_active
    }
    LEADS {
      uuid id PK
      uuid organization_id FK
      uuid owner_id FK
      uuid stage_id FK
      varchar name
      varchar company
      varchar email
      varchar phone
      varchar source
      varchar industry
      varchar region
      text notes
      varchar lost_reason
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
    }
    LEAD_TAGS {
      uuid lead_id PK,FK
      uuid tag_id PK,FK
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
    }
    TASKS {
      uuid id PK
      uuid organization_id FK
      uuid lead_id FK
      uuid assignee_id FK
      varchar title
      varchar status
      timestamptz due_at
      timestamptz completed_at
      timestamptz created_at
      timestamptz updated_at
    }
    STAGE_HISTORIES {
      uuid id PK
      uuid lead_id FK
      uuid from_stage_id FK
      uuid to_stage_id FK
      uuid changed_by_id FK
      text note
      timestamptz changed_at
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

- `USERS` 在组织内对 `email`、`phone` 做唯一约束；空值允许多个，规范化后再比较。
- `PIPELINE_STAGES` 在组织内对 `code` 唯一；只能有一个活动默认顺序位置。
- `LEAD_TAGS` 使用联合主键；删除线索或标签时按业务决定级联，审计记录不可级联删除。
- `LEADS` 建议索引 `(organization_id, owner_id, updated_at)`、`(organization_id, stage_id)`、`(organization_id, next_follow_up_at)`。
- `FOLLOW_UPS`、`TASKS` 的列表查询必须带 `organization_id`；禁止仅凭外部 ID 查询。
- 阶段历史和审计日志只追加，不提供普通用户更新接口；保留策略由后续合规需求另行定义。
