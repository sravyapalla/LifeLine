ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS address_text VARCHAR(240) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS landmark VARCHAR(160) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS location_source VARCHAR(40) NOT NULL DEFAULT 'COORDINATES';

CREATE TABLE IF NOT EXISTS hospital_applications (
    id VARCHAR(40) PRIMARY KEY,
    hospital_name VARCHAR(160) NOT NULL,
    contact_name VARCHAR(120) NOT NULL,
    contact_phone VARCHAR(40) NOT NULL,
    address_text VARCHAR(240) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    specialties VARCHAR(240) NOT NULL,
    total_beds INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ,
    CONSTRAINT hospital_applications_beds_chk CHECK (total_beds > 0),
    CONSTRAINT hospital_applications_status_chk CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);
