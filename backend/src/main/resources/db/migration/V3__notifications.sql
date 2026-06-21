CREATE TABLE notifications (
    id VARCHAR(40) PRIMARY KEY,
    role VARCHAR(20) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    event_id VARCHAR(40) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    CONSTRAINT notifications_role_chk CHECK (role IN ('PATIENT', 'DRIVER', 'HOSPITAL', 'CONTROL'))
);

CREATE INDEX notifications_role_created_idx ON notifications (role, created_at DESC);
CREATE INDEX notifications_unread_role_idx ON notifications (role, created_at DESC) WHERE acknowledged_at IS NULL;
