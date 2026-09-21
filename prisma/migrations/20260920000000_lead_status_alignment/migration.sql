BEGIN;

CREATE TYPE "lead_status" AS ENUM (
  'new',
  'contacted',
  'qualified',
  'proposal',
  'negotiation',
  'won',
  'lost'
);

ALTER TABLE "leads"
  ADD COLUMN "status" "lead_status",
  ADD COLUMN "closed_at" TIMESTAMPTZ(3),
  ADD COLUMN "outcome_note" VARCHAR(500),
  ADD COLUMN "lost_reason" VARCHAR(200);

ALTER TABLE "stage_history"
  ADD COLUMN "from_status" "lead_status",
  ADD COLUMN "to_status" "lead_status";

-- Map existing configurable pipeline rows to the fixed Stage 3 status values.
UPDATE "leads" l
SET "status" = CASE
  WHEN s."is_won" THEN 'won'::"lead_status"
  WHEN s."is_lost" THEN 'lost'::"lead_status"
  WHEN lower(s."code") = 'new' THEN 'new'::"lead_status"
  WHEN lower(s."code") = 'contacted' THEN 'contacted'::"lead_status"
  WHEN lower(s."code") = 'qualified' THEN 'qualified'::"lead_status"
  WHEN lower(s."code") = 'proposal' THEN 'proposal'::"lead_status"
  WHEN lower(s."code") = 'negotiation' THEN 'negotiation'::"lead_status"
  WHEN lower(s."code") = 'won' THEN 'won'::"lead_status"
  WHEN lower(s."code") = 'lost' THEN 'lost'::"lead_status"
  ELSE NULL
END
FROM "pipeline_stages" s
WHERE s."id" = l."stage_id"
  AND s."organization_id" = l."organization_id";

DO $$
DECLARE
  unmapped_report text;
BEGIN
  SELECT string_agg(
           format('organization_id=%s unmapped_count=%s', "organization_id", row_count),
           E'\n' ORDER BY "organization_id"
         )
    INTO unmapped_report
  FROM (
    SELECT "organization_id", count(*) AS row_count
    FROM "leads"
    WHERE "status" IS NULL
    GROUP BY "organization_id"
  ) unmapped;

  IF unmapped_report IS NOT NULL THEN
    RAISE EXCEPTION 'LEAD_STATUS_BACKFILL_UNMAPPED: %', unmapped_report;
  END IF;
END
$$;

-- Keep the legacy terminal timestamps, but make the new lead-owned timestamp authoritative.
UPDATE "leads"
SET "closed_at" = COALESCE("won_at", "lost_at");

-- Map old stage-history references while retaining the old columns for Stage 3.5.
UPDATE "stage_history" h
SET "from_status" = CASE
  WHEN s."is_won" THEN 'won'::"lead_status"
  WHEN s."is_lost" THEN 'lost'::"lead_status"
  WHEN lower(s."code") = 'new' THEN 'new'::"lead_status"
  WHEN lower(s."code") = 'contacted' THEN 'contacted'::"lead_status"
  WHEN lower(s."code") = 'qualified' THEN 'qualified'::"lead_status"
  WHEN lower(s."code") = 'proposal' THEN 'proposal'::"lead_status"
  WHEN lower(s."code") = 'negotiation' THEN 'negotiation'::"lead_status"
  WHEN lower(s."code") = 'won' THEN 'won'::"lead_status"
  WHEN lower(s."code") = 'lost' THEN 'lost'::"lead_status"
  ELSE NULL
END
FROM "pipeline_stages" s
WHERE s."id" = h."from_stage_id";

UPDATE "stage_history" h
SET "to_status" = CASE
  WHEN s."is_won" THEN 'won'::"lead_status"
  WHEN s."is_lost" THEN 'lost'::"lead_status"
  WHEN lower(s."code") = 'new' THEN 'new'::"lead_status"
  WHEN lower(s."code") = 'contacted' THEN 'contacted'::"lead_status"
  WHEN lower(s."code") = 'qualified' THEN 'qualified'::"lead_status"
  WHEN lower(s."code") = 'proposal' THEN 'proposal'::"lead_status"
  WHEN lower(s."code") = 'negotiation' THEN 'negotiation'::"lead_status"
  WHEN lower(s."code") = 'won' THEN 'won'::"lead_status"
  WHEN lower(s."code") = 'lost' THEN 'lost'::"lead_status"
  ELSE NULL
END
FROM "pipeline_stages" s
WHERE s."id" = h."to_stage_id";

DO $$
DECLARE
  unmapped_report text;
BEGIN
  SELECT string_agg(
           format('organization_id=%s unmapped_count=%s', l."organization_id", row_count),
           E'\n' ORDER BY l."organization_id"
         )
    INTO unmapped_report
  FROM (
    SELECT h."lead_id", count(*) AS row_count
    FROM "stage_history" h
    WHERE (h."to_stage_id" IS NOT NULL AND h."to_status" IS NULL)
       OR (h."from_stage_id" IS NOT NULL AND h."from_status" IS NULL)
    GROUP BY h."lead_id"
  ) unmapped
  JOIN "leads" l ON l."id" = unmapped."lead_id";

  IF unmapped_report IS NOT NULL THEN
    RAISE EXCEPTION 'STAGE_STATUS_BACKFILL_UNMAPPED: %', unmapped_report;
  END IF;
END
$$;

-- Copy legacy stage-history outcome data once; Stage 3 writes only the lead-owned fields.
UPDATE "leads" l
SET "outcome_note" = (
      SELECT sh."outcome_note"
      FROM "stage_history" sh
      WHERE sh."lead_id" = l."id"
        AND sh."to_status" = l."status"
      ORDER BY sh."created_at" DESC, sh."id" DESC
      LIMIT 1
    ),
    "lost_reason" = (
      SELECT sh."lost_reason"
      FROM "stage_history" sh
      WHERE sh."lead_id" = l."id"
        AND sh."to_status" = l."status"
      ORDER BY sh."created_at" DESC, sh."id" DESC
      LIMIT 1
    )
WHERE l."status" IN ('won'::"lead_status", 'lost'::"lead_status");

DO $$
DECLARE
  invalid_report text;
BEGIN
  SELECT string_agg(
           format('organization_id=%s invalid_count=%s', "organization_id", row_count),
           E'\n' ORDER BY "organization_id"
         )
    INTO invalid_report
  FROM (
    SELECT "organization_id", count(*) AS row_count
    FROM "leads"
    WHERE ("status" IN ('won'::"lead_status", 'lost'::"lead_status")
           AND ("closed_at" IS NULL OR NULLIF(btrim("outcome_note"), '') IS NULL
                OR ("status" = 'lost'::"lead_status" AND NULLIF(btrim("lost_reason"), '') IS NULL)))
       OR ("status" NOT IN ('won'::"lead_status", 'lost'::"lead_status")
           AND ("closed_at" IS NOT NULL OR "outcome_note" IS NOT NULL OR "lost_reason" IS NOT NULL))
    GROUP BY "organization_id"
  ) invalid;

  IF invalid_report IS NOT NULL THEN
    RAISE EXCEPTION 'LEAD_TERMINAL_DATA_INVALID: %', invalid_report;
  END IF;
END
$$;

ALTER TABLE "leads"
  ALTER COLUMN "status" SET NOT NULL;

ALTER TABLE "stage_history"
  ALTER COLUMN "to_status" SET NOT NULL;

-- Legacy stage references remain available for Stage 3.5 but are no longer required by Stage 3.
ALTER TABLE "leads"
  ALTER COLUMN "stage_id" DROP NOT NULL;

ALTER TABLE "stage_history"
  ALTER COLUMN "to_stage_id" DROP NOT NULL;

ALTER TABLE "leads"
  ADD CONSTRAINT "chk_closed_at_conditional"
  CHECK (
    ("status" IN ('won'::"lead_status", 'lost'::"lead_status") AND "closed_at" IS NOT NULL)
    OR ("status" NOT IN ('won'::"lead_status", 'lost'::"lead_status") AND "closed_at" IS NULL)
  ),
  ADD CONSTRAINT "chk_outcome_note_conditional"
  CHECK (
    ("status" IN ('won'::"lead_status", 'lost'::"lead_status") AND NULLIF(btrim("outcome_note"), '') IS NOT NULL)
    OR ("status" NOT IN ('won'::"lead_status", 'lost'::"lead_status") AND "outcome_note" IS NULL)
  ),
  ADD CONSTRAINT "chk_lost_reason_conditional"
  CHECK (
    ("status" = 'lost'::"lead_status" AND NULLIF(btrim("lost_reason"), '') IS NOT NULL)
    OR ("status" <> 'lost'::"lead_status" AND "lost_reason" IS NULL)
  );

-- Read-only duplicate preflight. The report contains organization IDs and counts only.
DO $$
DECLARE
  duplicate_report text;
BEGIN
  SELECT string_agg(
           format('organization_id=%s duplicate_count=%s', "organization_id", duplicate_count),
           E'\n' ORDER BY "organization_id"
         )
    INTO duplicate_report
  FROM (
    SELECT "organization_id", sum(row_count) AS duplicate_count
    FROM (
      SELECT "organization_id",
             "normalized_email" AS normalized_value, count(*) AS row_count
      FROM "leads"
      WHERE "normalized_email" IS NOT NULL
      GROUP BY "organization_id", "normalized_email"
      HAVING count(*) > 1
      UNION ALL
      SELECT "organization_id",
             "normalized_phone" AS normalized_value, count(*) AS row_count
      FROM "leads"
      WHERE "normalized_phone" IS NOT NULL
      GROUP BY "organization_id", "normalized_phone"
      HAVING count(*) > 1
    ) duplicate_groups
    GROUP BY "organization_id"
  ) duplicates;

  IF duplicate_report IS NOT NULL THEN
    RAISE EXCEPTION 'LEAD_CONTACT_DUPLICATES: %', duplicate_report;
  END IF;
END
$$;

CREATE UNIQUE INDEX "leads_org_normalized_email_unique"
  ON "leads" ("organization_id", "normalized_email")
  WHERE "normalized_email" IS NOT NULL;

CREATE UNIQUE INDEX "leads_org_normalized_phone_unique"
  ON "leads" ("organization_id", "normalized_phone")
  WHERE "normalized_phone" IS NOT NULL;

CREATE INDEX "leads_organization_id_status_updated_at_idx"
  ON "leads" ("organization_id", "status", "updated_at");

COMMIT;
