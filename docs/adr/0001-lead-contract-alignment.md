# ADR 0001: Lead contract alignment

- Status: Accepted
- Date: 2026-09-20
- Scope: Stage 3 lead module

## Context

The Java API already contains Stage 2 implementations for authentication, users, teams, leads, configurable pipeline stages, imports, and audit logging. The persistence layer uses Spring JDBC `JdbcTemplate`, while `prisma/schema.prisma` and immutable Prisma migrations define the PostgreSQL schema.

The reviewed API and ER documents drifted from the running implementation in several places: role names, API prefixes, the API port, normalized contact column names, configurable stages versus a fixed lead lifecycle, and the reliability of asynchronous imports. Stage 3 must align those contracts without introducing a second persistence stack or deleting Stage 2 database structures that will be reused in Stage 3.5.

## Decision 1: Preserve the existing role model

The supported role codes remain `OWNER`, `ADMIN`, `SALES`, and `SUPPORT` in Java, PostgreSQL, TypeScript, JWT claims, and API payloads.

- `OWNER` and `ADMIN` have organization-wide lead administration permissions.
- `SALES` can create leads and read or modify only leads assigned to that user, subject to the transition rules below.
- `SUPPORT` is the organization-wide read-only role. It cannot create, update, assign, archive, restore, import, export, or change the status of a lead.
- Background import workers are service components, not user roles, and do not receive user access tokens.

The Stage 2 authorization code and role enum are retained. `docs/api.md` is updated to match the implementation rather than replacing `SUPPORT` with `viewer` or adding a `worker` user role.

## Decision 2: Continue with JdbcTemplate

Stage 3 continues to use Spring JDBC `JdbcTemplate`, parameterized SQL, and Spring transaction boundaries. JPA, Hibernate, MyBatis, Prisma Client, and any second runtime persistence mechanism are prohibited.

Lead SQL may be moved from services into focused repository classes, but those repositories remain thin `JdbcTemplate` adapters. Controllers handle HTTP adaptation and validation, services own authorization-aware business rules and transactions, and repositories own SQL and row mapping.

Java persistence records/classes are explicit JDBC mapping models, not JPA entities. API DTOs remain explicit records/classes with Bean Validation and never use raw `Object` or `Map` as business contracts.

## Decision 3: Use a fixed LeadStatus state machine and retain pipeline tables for Stage 3.5

Stage 3 introduces the fixed enum `LeadStatus` with these persisted lowercase values:

```text
new
contacted
qualified
proposal
negotiation
won
lost
```

The forward lifecycle is:

```text
new -> contacted -> qualified -> proposal -> negotiation -> won|lost
```

The transition matrix is implemented in one Java class. `SALES` may move an assigned, unarchived lead only to the next open status and may move `negotiation` to `won` or `lost`. `OWNER` and `ADMIN` may correct an unarchived lead to any different status and may reopen a terminal lead. `SUPPORT` cannot transition leads. Repeating the current status, changing an archived lead, or violating the role-specific matrix returns `409 STAGE_INVALID_TRANSITION`.

Entering `won` or `lost` requires `outcomeNote`; entering `lost` additionally requires `lostReason`. A terminal transition sets `closedAt`. Reopening clears `closedAt`, `outcomeNote`, and `lostReason`. The lead update, status-history append, and audit-log append commit in one transaction after locking the lead row.

The schema migration is additive with compatibility-only constraint relaxation:

- Add `leads.status` using a PostgreSQL enum that matches `LeadStatus`.
- Add `leads.closed_at`, `leads.outcome_note`, and `leads.lost_reason` so terminal outcome data belongs to the lead's current state.
- Add `stage_history.from_status` and `stage_history.to_status` using the same enum.
- Backfill statuses from existing pipeline rows. `is_won=true` maps to `won`, `is_lost=true` maps to `lost`, and recognized codes map to the remaining lowercase status values. Unmappable rows abort the migration without changing data.
- Retain `leads.stage_id`, `stage_history.from_stage_id`, `stage_history.to_stage_id`, the `pipeline_stages` table, their foreign keys, and all existing data.
- After backfill, relax only the `NOT NULL` constraints on legacy `leads.stage_id` and `stage_history.to_stage_id`. This is required because Stage 3 code must not read or write those legacy columns. No legacy column, constraint, table, or row is dropped.
- `status` is `NOT NULL`; `closed_at`, `outcome_note`, and `lost_reason` remain nullable and are enforced by conditional CHECK constraints per status. `won|lost` requires `closed_at` and `outcome_note`; `lost` additionally requires `lost_reason`; non-terminal statuses require all three fields to be `NULL`.
- When `OWNER` or `ADMIN` reopens a terminal lead, the request must target a non-terminal status (`new`, `contacted`, `qualified`, `proposal`, or `negotiation`). If no target is specified, the service defaults to `negotiation`. A target of `won` or `lost` is rejected with `409 STAGE_INVALID_TRANSITION`.

`PipelineStageController` and `PipelineStageService` are removed from the Java runtime in Stage 3 so no configurable-stage routes remain exposed. Their database table and data remain untouched for a clean Stage 3.5 implementation. The existing `LeadService` is refactored in place, rather than leaving a second lead implementation beside it.

## Decision 4: Standardize business routes on /api/v1

All business API routes use `/api/v1/*`. The temporary duplicate `/api/*` Controller mappings are removed only after these consumers have been updated:

1. Change the frontend Axios base URL to `/api/v1`.
2. Add the Vite development proxy `/api/v1 -> http://localhost:8080`.
3. Update environment examples, E2E tests, API examples, and README commands.
4. Remove the old `/api/*` business mappings from Controllers.
5. Run route and E2E checks to prove no client still depends on the old prefix.

Swagger is development tooling rather than a versioned business API. Swagger UI remains at `/api/docs`, OpenAPI JSON remains at `/api/docs-json`, and the `WebConfig` authentication exclusions retain `/api/docs*`. Swagger is not moved under `/api/v1`.

## Decision 5: Standardize the Java API port on 8080

The Java API default and container port are `8080`. Stage 3 synchronizes `application.yml`, `.env.example`, `docker-compose.yml`, `README.md`, `VITE_API_BASE_URL`, the Vite proxy, and E2E configuration. PostgreSQL remains exposed on host port `55432`, and the AI service remains on `8000`.

## Decision 6: Prisma is the schema source and normalized column names remain unchanged

`prisma/schema.prisma` is the declaration source, and new immutable files under `prisma/migrations/` are the only deployment mechanism. Existing migrations are never edited, reordered, or rolled back.

The canonical normalized contact columns are the existing `normalized_email` and `normalized_phone`. The alternatives `email_normalized` and `phone_normalized` are rejected; Stage 3 must not add or dual-write them.

Email normalization trims surrounding whitespace and lowercases with `Locale.ROOT`. Phone normalization trims whitespace, removes separators such as spaces, hyphens, and parentheses, retains one valid leading `+`, and validates the remaining digit length. A lead is a duplicate when either normalized email or normalized phone matches another lead in the same organization. Archived leads remain in the duplicate domain.

Before creating uniqueness indexes, the migration performs read-only collision guards grouped by `organization_id` for both normalized columns. Any collision aborts the migration. The diagnostic report contains only `organization_id` and duplicate counts; it never contains raw or normalized email/phone values.

The migration then replaces the current non-unique lead contact indexes with organization-scoped partial unique indexes:

```sql
CREATE UNIQUE INDEX ... ON leads (organization_id, normalized_email)
WHERE normalized_email IS NOT NULL;

CREATE UNIQUE INDEX ... ON leads (organization_id, normalized_phone)
WHERE normalized_phone IS NOT NULL;
```

The only excluded rows are those whose corresponding normalized value is `NULL`. Archived rows are not excluded. Application-level duplicate lookup remains for a useful `LEAD_DUPLICATE` response, while the unique indexes are the concurrency-safe final authority.

## Import and export decisions

The existing in-process `@Async` import dispatch and hand-written line parser are replaced in place.

- `import_jobs` is the durable queue. A worker claims queued or recoverable stale-running jobs using a short transaction with `FOR UPDATE SKIP LOCKED`, records ownership/start time, and processes outside the claim transaction. A restart can recover unfinished jobs from PostgreSQL.
- Apache Commons CSV is selected instead of opencsv. It supports quoted delimiters, escaped quotes, embedded newlines, headers, and streaming records, avoiding custom CSV parsing. UTF-8 BOM handling is explicit at the reader boundary. The hand-written parser is removed.
- File-level validation completes before any lead row is inserted. Row-level validation reuses the same normalization, deduplication, authorization, and persistence rules as manual creation.
- Import errors and generated error files contain no unmasked personal data. Database rows store controlled storage keys, not arbitrary host paths or CSV contents.
- Export is synchronous and streamed with a fixed row limit. Only `OWNER` and `ADMIN` may export. Values beginning with `=`, `+`, `-`, or `@` after leading whitespace are escaped to prevent spreadsheet formula injection. Response filenames are generated by the server from a safe ASCII pattern.

## HTTP error semantics

- `403 FORBIDDEN`: an authenticated caller lacks permission for an organization-visible operation, such as `SALES` filtering by another user or `SUPPORT` attempting export.
- `404 RESOURCE_NOT_FOUND`: the resource does not exist, belongs to another organization, or is outside the caller's resource visibility. This prevents cross-organization and cross-owner existence disclosure.
- `409 LEAD_DUPLICATE`: a normalized email or phone collides within the organization. `candidateLeadIds` contains only IDs visible to the caller.
- `409 STAGE_INVALID_TRANSITION`: the requested status change violates the fixed state machine or the lead cannot currently transition.

All failures use the standard `{ data, meta, error }` JSON envelope. CSV export success is the documented exception and returns `text/csv`; export errors still use the JSON envelope.

## Contract alignment checkpoints

Before Stage 3 is complete, each API field and enum is checked across these representations:

1. `docs/api.md` request, response, permission, and error contract.
2. `prisma/schema.prisma` column name, type, nullability, index, and enum value.
3. Java JDBC model, request/response DTO, row mapper, parameterized SQL, and OpenAPI schema.
4. `packages/shared` TypeScript enum and DTO used by the Vue client.
5. MockMvc and PostgreSQL integration fixtures that assert the serialized JSON and persisted values.

The TypeScript package remains frontend-only; Java does not depend on it. Contract alignment is verified by tests and review rather than runtime code generation between languages.

## Consequences

- Stage 3 has one persistence stack, one lead implementation, and one fixed status authority.
- Existing pipeline data remains recoverable for Stage 3.5 but is not exposed or mutated by Stage 3 runtime code.
- The migration may deliberately fail when legacy stages cannot be mapped or duplicate contacts exist. Operators must resolve the reported organization-level conflicts before retrying; the migration never silently merges or overwrites leads.
- Removing legacy route aliases and changing the port are coordinated compatibility breaks and must follow the documented consumer-first order.
