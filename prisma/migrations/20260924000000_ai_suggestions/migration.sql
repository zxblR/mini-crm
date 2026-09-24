CREATE TYPE ai_suggestion_type AS ENUM (
  'INTENT_SCORE',
  'FOLLOW_UP_SUMMARY',
  'NEXT_ACTION',
  'SCRIPT',
  'WAKE_UP'
);

CREATE TABLE ai_suggestions (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  lead_id UUID NOT NULL REFERENCES leads(id),
  created_by UUID NOT NULL REFERENCES users(id),
  suggestion_type ai_suggestion_type NOT NULL,
  input_hash VARCHAR(64) NOT NULL,
  prompt_version VARCHAR(32) NOT NULL,
  result JSONB NOT NULL,
  model VARCHAR(128) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ai_suggestions_cache_key UNIQUE (
    organization_id,
    lead_id,
    suggestion_type,
    input_hash,
    prompt_version
  )
);

CREATE INDEX ai_suggestions_organization_lead_idx
  ON ai_suggestions (organization_id, lead_id);

CREATE INDEX ai_suggestions_expires_at_idx
  ON ai_suggestions (expires_at);
