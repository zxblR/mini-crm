# ADR 0002: Dashboard statistics scope and metrics

- Status: Accepted
- Date: 2026-09-23
- Scope: Stage 5 reporting

## Context

The current application already exposes `/api/v1/dashboard/*`. Statistics must extend that contract without creating a parallel `/stats/*` API. `prisma/schema.prisma` is the sole database schema authority. Reporting must use the fixed lead lifecycle introduced in Stage 3 and must not infer business outcomes from legacy configurable pipeline data.

## Decisions

1. Extend `/api/v1/dashboard/*`; do not introduce `/api/v1/stats/*`. Existing dashboard routes and response fields remain compatible. New fields and routes are additive.
2. Stage 5 reports counts and durations only. Revenue, average deal value, ROI, channel cost, AI-generated analysis, and frontend pages are excluded. Monetary reporting is deferred to Stage 5.5 after the required schema fields are explicitly designed.
3. Response time is a proxy: `first non-deleted follow_ups.occurred_at - leads.created_at`. It is not a verified first human response SLA.
4. Win/loss is determined only from the current `leads.status` together with `leads.closed_at`. Legacy `won_at` and `lost_at` are ignored. Sales win attribution uses the current `leads.owner_id`, not `stage_history.actor_id`; reassignment can therefore change historical owner rankings.
5. PostgreSQL integration tests must use a dedicated `mini_crm_test` database. The test connection is supplied only by `MINI_CRM_TEST_DATABASE_URL` (and test credentials by `MINI_CRM_TEST_DATABASE_USERNAME` / `MINI_CRM_TEST_DATABASE_PASSWORD`); it must be a PostgreSQL URL whose database path is exactly `/mini_crm_test`. Tests skip when this explicit test URL is absent and must never fall back to `JAVA_DATABASE_URL`, `.env`, or `mini_crm`. Synthetic fixtures run inside one transaction marked rollback-only; they are never committed or cleaned from the development database.
6. The response-time aggregate includes `respondedCount`, `unrespondedCount`, and `avgResponseSeconds`, where the average uses responded leads only. Distribution has a distinct `unresponded` bucket; it is not silently blended into `>7d`.
7. Stage numbering is corrected: Stage 5 is reporting (this work); the former Stage 5 pipeline board moves to the Stage 3.5 pipeline backlog; former Stage 7 dashboard work is merged into Stage 5. The roadmap keeps a history note.
8. No new index is assumed. Run synthetic-data `EXPLAIN (ANALYZE, BUFFERS)` against the dedicated test database. Add an index only if a core table (`leads` or `follow_ups`) shows a `Seq Scan`; any approved index must be a new immutable migration. Never edit an applied migration.
9. Paginated report lists reuse `PageSupport` and the existing `{data,meta,error}` envelope. There is no Redis cache, materialized view, new framework, or asynchronous reporting job.
10. `prisma/schema.prisma` is the only schema authority. The ER diagram is corrected as documentation only; field, relation or index changes require a new immutable migration and are outside this change unless the measured `Seq Scan` criterion above is met.

## Metric definitions

- Lead scope: all unarchived leads for the selected organization/person. New-lead cohort: leads with `created_at >= from AND created_at < to`.
- Lead volume: count of all leads in scope. New leads: count of the new-lead cohort.
- Status distribution: current `leads.status` counts over all leads in scope.
- Conversion rate: current leads in the new-lead cohort whose status is `won` and `closed_at` is non-null, divided by the new-lead cohort count; zero for an empty cohort.
- Channel conversion: the same new-lead cohort conversion numerator/denominator grouped by `leads.source`.
- Sales follow-ups: count of non-deleted `follow_ups` occurring in `[from,to)` that belong to leads in the new-lead cohort, grouped by the lead's current owner. This is current-owner attribution, not creator attribution.
- Sales wins: new-lead cohort leads currently `won` with non-null `closed_at`, grouped by current `owner_id`. Deal cycle is average `closed_at - created_at` for those leads only. closed_at < created_at 的记录视为脏数据，不计入 avgDealCycleSeconds 样本，但仍计入 wonCount。
- Trend: lead count is grouped by `created_at`; won count is grouped by `closed_at` for current `status='won'`. Buckets are UTC calendar day, week (Monday start), or month.
- Loss reasons: count current `status='lost'` leads closed in `[from,to)`, grouped by `lost_reason`; null/blank reasons are presented as `unspecified` for legacy rows.
- Response time: for each cohort lead, use its earliest `occurred_at` among `follow_ups.deleted_at IS NULL`; response seconds are `max(0, first occurred_at - created_at)` to keep backdated events from producing negative durations. Null first-follow-up is unresponded. Average excludes unresponded leads. Buckets: `<1h`, `1h-24h`, `1d-7d`, `>7d`, and `unresponded`.

## Query and performance policy

All filters include `organization_id`; SALES queries additionally force the current user's `owner_id`. Time request parameters are ISO-8601 offset timestamps (`OffsetDateTime`) and JDBC values are normalized through `JdbcTimeUtils.toDbTime`. Reporting groups timestamps in UTC using PostgreSQL `date_trunc`. Every report is bounded to a maximum 366-day interval. Lists use `PageSupport` (page 1-based, default 20, maximum pageSize 100); trend output is bounded by the interval/granularity. Initial implementation adds no index migration. Synthetic data plans must be captured before any index decision.

Repository query shapes and data sources:

- Overview: aggregate unarchived `leads` by current `status`, count `created_at` in range, and calculate won count / `closed_at - created_at`; response-time CTE left-joins `follow_ups` on both `lead_id` and `organization_id`, filters `deleted_at IS NULL`, and groups by lead to obtain the first `occurred_at`.
- Channels: `leads` filtered by organization, archive state, created-time range and optional owner; group by `source`, with a filtered count for current won status.
- Sales ranking: cohort CTE over `leads`; join `users` by id and organization for display name; join a follow-up aggregate for cohort leads grouped by current lead owner, counting non-deleted follow-ups in range. Wins and cycle use current lead owner.
- Trend: UTC `generate_series` of buckets joined to separate `leads.created_at` and current-won `leads.closed_at` aggregates. The granularity token and interval are selected only from the `Granularity` enum, never interpolated from arbitrary request text.
- Loss reasons: filtered `leads` grouped by `COALESCE(NULLIF(BTRIM(lost_reason), ''), 'unspecified')`; total count is obtained separately for compatible `PageSupport` metadata.
- Response-time distribution: one `leads LEFT JOIN follow_ups` aggregate grouped per lead, with a distinct null bucket and a five-bucket response. It is not paginated.

Likely hotspots are date-range scans and hash/group aggregation on large `leads`, follow-up joins/grouping for response time and sales ranking, and count queries used for list metadata. Existing relevant indexes from Prisma include `leads(organization_id, owner_id, archived_at)`, `leads(organization_id, status, updated_at)`, `leads(organization_id, source, created_at)`, and `follow_ups(organization_id, lead_id, deleted_at, occurred_at)`; there is no declared `leads(organization_id, created_at)` index. No new index is declared before measurement. On synthetic data loaded only in `mini_crm_test`, capture `EXPLAIN (ANALYZE, BUFFERS)` for the cohort lead scan, first-follow-up aggregate, sales follow-up aggregate, and loss-reason group. Only a material sequential scan on a core table can justify a new migration; record evidence and leave applied migrations untouched. There is no cache or materialized view.

## Audit and privacy

Read-only report views do not create per-request activity-log rows. If a future export is added, it must emit an `EXPORT` action with non-PII metadata only (report key, UTC range, granularity, and row count); it must not include names, phones, emails, source free text beyond the report's controlled grouping, or raw filters that may contain PII. Stage 5 does not add export endpoints.

## Consequences

The metrics describe current lead state and current owner, not a full historical snapshot. Reopening or reassigning a lead can alter prior-period outcome reports. The response metric is an operational proxy and may include backdated follow-ups. Financial efficiency cannot be measured until Stage 5.5 adds and migrates the relevant fields.

## Roadmap stage numbering adjustment

- Stage 5 is statistics and reporting (this change).
- The former Stage 5 pipeline board is moved to the Stage 3.5 pipeline backlog.
- The former Stage 7 dashboard work is merged into Stage 5; its historical numbering is retained in the roadmap note.
