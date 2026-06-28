CREATE TABLE IF NOT EXISTS user_accounts (
    username VARCHAR(160) NOT NULL,
    role VARCHAR(30) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    ambulance_id VARCHAR(40),
    hospital_id VARCHAR(40),
    status VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (username, role),
    CONSTRAINT user_accounts_role_chk CHECK (role IN ('PATIENT', 'DRIVER', 'HOSPITAL', 'CONTROL')),
    CONSTRAINT user_accounts_status_chk CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS user_accounts_status_role_idx
    ON user_accounts (status, role, updated_at DESC);

CREATE INDEX IF NOT EXISTS user_accounts_ambulance_idx
    ON user_accounts (ambulance_id)
    WHERE ambulance_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS user_accounts_hospital_idx
    ON user_accounts (hospital_id)
    WHERE hospital_id IS NOT NULL;
