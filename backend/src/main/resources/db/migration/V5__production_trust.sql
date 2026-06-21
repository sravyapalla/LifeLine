ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS requester_user_id VARCHAR(120) NOT NULL DEFAULT 'patient.demo';

CREATE INDEX IF NOT EXISTS incidents_requester_user_idx
    ON incidents (requester_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS security_audit_events (
    id VARCHAR(40) PRIMARY KEY,
    actor_user_id VARCHAR(120) NOT NULL,
    actor_role VARCHAR(20) NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(120) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    reason TEXT NOT NULL,
    metadata JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS security_audit_events_created_idx
    ON security_audit_events (created_at DESC);

CREATE INDEX IF NOT EXISTS security_audit_events_actor_idx
    ON security_audit_events (actor_user_id, created_at DESC);
