INSERT INTO organizations (id, name, slug, timezone, is_active, created_at, updated_at)
VALUES ('00000000-0000-4000-8000-000000000001', 'Mini CRM Demo Team', 'mini-crm-demo', 'Asia/Shanghai', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, slug = EXCLUDED.slug, timezone = EXCLUDED.timezone, is_active = true, updated_at = CURRENT_TIMESTAMP;

INSERT INTO pipeline_stages (id, organization_id, code, name, color, sort_order, is_default, is_won, is_lost, is_active, created_at, updated_at)
VALUES
  ('00000000-0000-4000-8000-000000000101', '00000000-0000-4000-8000-000000000001', 'new', '新建', '#64748B', 1, true, false, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('00000000-0000-4000-8000-000000000102', '00000000-0000-4000-8000-000000000001', 'contacted', '已联系', '#2563EB', 2, false, false, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('00000000-0000-4000-8000-000000000103', '00000000-0000-4000-8000-000000000001', 'qualified', '需求确认', '#0891B2', 3, false, false, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('00000000-0000-4000-8000-000000000104', '00000000-0000-4000-8000-000000000001', 'proposal', '报价/方案', '#7C3AED', 4, false, false, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('00000000-0000-4000-8000-000000000105', '00000000-0000-4000-8000-000000000001', 'negotiation', '谈判', '#D97706', 5, false, false, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('00000000-0000-4000-8000-000000000106', '00000000-0000-4000-8000-000000000001', 'won', '赢单', '#16A34A', 6, false, true, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('00000000-0000-4000-8000-000000000107', '00000000-0000-4000-8000-000000000001', 'lost', '输单', '#DC2626', 7, false, false, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, sort_order = EXCLUDED.sort_order, is_default = EXCLUDED.is_default, is_won = EXCLUDED.is_won, is_lost = EXCLUDED.is_lost, is_active = true, updated_at = CURRENT_TIMESTAMP;

INSERT INTO users (
  id,
  organization_id,
  name,
  email,
  normalized_email,
  password_hash,
  is_active,
  created_at,
  updated_at
)
VALUES
  (
    '00000000-0000-4000-8000-000000000011',
    '00000000-0000-4000-8000-000000000001',
    'Demo Owner',
    'admin@example.com',
    LOWER('admin@example.com'),
    '$argon2id$v=19$m=65536,t=3,p=4$b2hXp8gvbGAG8YNZyPOB2A$uX4N3EZbCtkhoUHXl0cDmJwUGtiHJCIKYN7wTl9uybg',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
  ),
  (
    '00000000-0000-4000-8000-000000000012',
    '00000000-0000-4000-8000-000000000001',
    'Demo Sales',
    'sales@example.com',
    LOWER('sales@example.com'),
    '$argon2id$v=19$m=65536,t=3,p=4$b2hXp8gvbGAG8YNZyPOB2A$uX4N3EZbCtkhoUHXl0cDmJwUGtiHJCIKYN7wTl9uybg',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
  ),
  (
    '00000000-0000-4000-8000-000000000013',
    '00000000-0000-4000-8000-000000000001',
    'Demo Admin',
    'admin2@example.com',
    LOWER('admin2@example.com'),
    '$argon2id$v=19$m=65536,t=3,p=4$b2hXp8gvbGAG8YNZyPOB2A$uX4N3EZbCtkhoUHXl0cDmJwUGtiHJCIKYN7wTl9uybg',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
  ),
  (
    '00000000-0000-4000-8000-000000000014',
    '00000000-0000-4000-8000-000000000001',
    'Demo Viewer',
    'viewer@example.com',
    LOWER('viewer@example.com'),
    '$argon2id$v=19$m=65536,t=3,p=4$b2hXp8gvbGAG8YNZyPOB2A$uX4N3EZbCtkhoUHXl0cDmJwUGtiHJCIKYN7wTl9uybg',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
  )
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, code, created_at)
VALUES
  ('00000000-0000-4000-8000-000000000011', 'OWNER', CURRENT_TIMESTAMP),
  ('00000000-0000-4000-8000-000000000013', 'ADMIN', CURRENT_TIMESTAMP),
  ('00000000-0000-4000-8000-000000000012', 'SALES', CURRENT_TIMESTAMP),
  ('00000000-0000-4000-8000-000000000014', 'SUPPORT', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

WITH lead_times AS (
  SELECT
    '00000000-0000-4000-8000-000000000301'::uuid AS id,
    '示例线索甲'::varchar AS name,
    '示例企业甲'::varchar AS company,
    'website'::varchar AS source,
    'new'::lead_status AS status,
    '00000000-0000-4000-8000-000000000012'::uuid AS owner_id,
    CURRENT_TIMESTAMP - INTERVAL '12 days' AS created_at
  UNION ALL
  SELECT
    '00000000-0000-4000-8000-000000000302'::uuid,
    '示例线索乙'::varchar,
    '示例企业乙'::varchar,
    'referral'::varchar,
    'contacted'::lead_status,
    '00000000-0000-4000-8000-000000000012'::uuid,
    CURRENT_TIMESTAMP - INTERVAL '9 days'
  UNION ALL
  SELECT
    '00000000-0000-4000-8000-000000000303'::uuid,
    '示例线索丙'::varchar,
    '示例企业丙'::varchar,
    'event'::varchar,
    'won'::lead_status,
    '00000000-0000-4000-8000-000000000011'::uuid,
    CURRENT_TIMESTAMP - INTERVAL '6 days'
  UNION ALL
  SELECT
    '00000000-0000-4000-8000-000000000304'::uuid,
    '示例线索丁'::varchar,
    '示例企业丁'::varchar,
    'import'::varchar,
    'lost'::lead_status,
    '00000000-0000-4000-8000-000000000012'::uuid,
    CURRENT_TIMESTAMP - INTERVAL '3 days'
),
demo_leads AS (
  SELECT
    id,
    name,
    company,
    source,
    status,
    owner_id,
    created_at,
    CASE
      WHEN status = 'won'::lead_status THEN created_at + INTERVAL '2 days'
      WHEN status = 'lost'::lead_status THEN created_at + INTERVAL '1 day'
      ELSE NULL
    END AS closed_at,
    CASE
      WHEN status = 'won'::lead_status THEN '演示成交记录'
      WHEN status = 'lost'::lead_status THEN '演示丢失记录'
      ELSE NULL
    END::varchar AS outcome_note,
    CASE WHEN status = 'lost'::lead_status THEN '预算未批准' ELSE NULL END::varchar AS lost_reason
  FROM lead_times
)
INSERT INTO leads (
  id,
  organization_id,
  name,
  company,
  source,
  status,
  owner_id,
  created_by_id,
  closed_at,
  outcome_note,
  lost_reason,
  created_at,
  updated_at
)
SELECT
  id,
  '00000000-0000-4000-8000-000000000001'::uuid,
  name,
  company,
  source,
  status,
  owner_id,
  '00000000-0000-4000-8000-000000000011'::uuid,
  closed_at,
  outcome_note,
  lost_reason,
  created_at,
  created_at
FROM demo_leads
ON CONFLICT DO NOTHING;

WITH demo_follow_ups AS (
  SELECT
    '00000000-0000-4000-8000-000000000401'::uuid AS id,
    '00000000-0000-4000-8000-000000000301'::uuid AS lead_id,
    '00000000-0000-4000-8000-000000000011'::uuid AS created_by_id,
    'call'::follow_up_type AS type,
    CURRENT_TIMESTAMP - INTERVAL '2 days' AS created_at,
    '首次电话沟通'::varchar AS summary,
    (CURRENT_TIMESTAMP - INTERVAL '2 days') + INTERVAL '2 days' AS next_step_at
  UNION ALL
  SELECT
    '00000000-0000-4000-8000-000000000402'::uuid,
    '00000000-0000-4000-8000-000000000302'::uuid,
    '00000000-0000-4000-8000-000000000012'::uuid,
    'wechat'::follow_up_type,
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    '微信确认需求'::varchar,
    NULL::timestamptz
)
INSERT INTO follow_ups (
  id,
  organization_id,
  lead_id,
  created_by_id,
  type,
  occurred_at,
  summary,
  next_step_at,
  created_at,
  updated_at
)
SELECT
  id,
  '00000000-0000-4000-8000-000000000001'::uuid,
  lead_id,
  created_by_id,
  type,
  created_at,
  summary,
  next_step_at,
  created_at,
  created_at
FROM demo_follow_ups
ON CONFLICT DO NOTHING;

INSERT INTO tasks (
  id,
  organization_id,
  lead_id,
  assignee_id,
  created_by_id,
  title,
  due_at,
  status,
  created_at,
  updated_at
)
VALUES
  (
    '00000000-0000-4000-8000-000000000501',
    '00000000-0000-4000-8000-000000000001',
    '00000000-0000-4000-8000-000000000301',
    '00000000-0000-4000-8000-000000000012',
    '00000000-0000-4000-8000-000000000011',
    '跟进示例线索甲',
    CURRENT_TIMESTAMP + INTERVAL '1 day',
    'pending',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
  ),
  (
    '00000000-0000-4000-8000-000000000502',
    '00000000-0000-4000-8000-000000000001',
    '00000000-0000-4000-8000-000000000302',
    '00000000-0000-4000-8000-000000000012',
    '00000000-0000-4000-8000-000000000011',
    '发送报价',
    CURRENT_TIMESTAMP + INTERVAL '3 days',
    'pending',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
  )
ON CONFLICT DO NOTHING;
