CREATE TABLE IF NOT EXISTS command_log (
    command_id UUID PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    type TEXT NOT NULL,
    payload JSONB NOT NULL,
    event_ids UUID[],
    status TEXT NOT NULL DEFAULT 'SUCCEEDED',
    error TEXT
);

-- Idempotent upgrades for existing databases (schema.sql runs on every startup).
-- DEFAULT 'SUCCEEDED' only backfills legacy rows, all of which succeeded; new
-- inserts set status explicitly to 'PENDING'.
ALTER TABLE command_log ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'SUCCEEDED';
ALTER TABLE command_log ADD COLUMN IF NOT EXISTS error TEXT;

CREATE TABLE IF NOT EXISTS event_log (
    sequence BIGINT PRIMARY KEY,
    event_id UUID NOT NULL,
    command_id UUID REFERENCES command_log(command_id),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    type TEXT NOT NULL,
    payload JSONB NOT NULL
);

-- Per-row event-schema version stamp (see docs/LegacyEventEagerMigrationPlan.md). NULL means
-- "unstamped" — a row written before this column existed, whose payload may be legacy- or
-- current-shape; the eager migration stamps every such row with its type's current version. New
-- appends stamp it explicitly, so only pre-migration rows are ever NULL. Deliberately no DEFAULT:
-- legacy event_log rows are a *mix* of versions per type, so no single backfill value is correct.
ALTER TABLE event_log ADD COLUMN IF NOT EXISTS schema_version INTEGER;
