CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE ambulances (
    id VARCHAR(40) PRIMARY KEY,
    call_sign VARCHAR(120) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    base_station VARCHAR(120) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ambulances_type_chk CHECK (type IN ('BLS', 'ALS', 'ICU')),
    CONSTRAINT ambulances_status_chk CHECK (status IN ('AVAILABLE', 'RESERVED', 'ON_TRIP', 'OFFLINE'))
);

CREATE INDEX ambulances_status_type_idx ON ambulances (status, type);
CREATE INDEX ambulances_location_postgis_idx
    ON ambulances USING gist (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326));

CREATE TABLE hospitals (
    id VARCHAR(40) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    total_beds INTEGER NOT NULL,
    available_beds INTEGER NOT NULL,
    quality_score DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT hospitals_beds_chk CHECK (total_beds >= 0 AND available_beds >= 0 AND available_beds <= total_beds),
    CONSTRAINT hospitals_quality_chk CHECK (quality_score >= 0 AND quality_score <= 1)
);

CREATE INDEX hospitals_capacity_idx ON hospitals (available_beds);
CREATE INDEX hospitals_location_postgis_idx
    ON hospitals USING gist (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326));

CREATE TABLE hospital_specialties (
    hospital_id VARCHAR(40) NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE,
    condition VARCHAR(30) NOT NULL,
    PRIMARY KEY (hospital_id, condition),
    CONSTRAINT hospital_specialties_condition_chk CHECK (condition IN ('CARDIAC', 'TRAUMA', 'PEDIATRIC', 'STROKE', 'GENERAL'))
);

CREATE TABLE incidents (
    id VARCHAR(40) PRIMARY KEY,
    requester_user_id VARCHAR(120) NOT NULL DEFAULT 'patient.demo',
    patient_name VARCHAR(160) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    condition VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT incidents_condition_chk CHECK (condition IN ('CARDIAC', 'TRAUMA', 'PEDIATRIC', 'STROKE', 'GENERAL')),
    CONSTRAINT incidents_priority_chk CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT incidents_status_chk CHECK (status IN ('NEW', 'ASSIGNED', 'CANCELLED', 'COMPLETED'))
);

CREATE INDEX incidents_status_created_idx ON incidents (status, created_at DESC);
CREATE INDEX incidents_requester_user_idx ON incidents (requester_user_id, created_at DESC);
CREATE INDEX incidents_location_postgis_idx
    ON incidents USING gist (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326));

CREATE TABLE trips (
    id VARCHAR(40) PRIMARY KEY,
    incident_id VARCHAR(40) NOT NULL REFERENCES incidents(id),
    ambulance_id VARCHAR(40) NOT NULL REFERENCES ambulances(id),
    hospital_id VARCHAR(40) NOT NULL REFERENCES hospitals(id),
    pickup_eta_minutes DOUBLE PRECISION NOT NULL,
    hospital_eta_minutes DOUBLE PRECISION NOT NULL,
    total_cost DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,
    CONSTRAINT trips_status_chk CHECK (status IN ('RESERVED', 'EN_ROUTE_PATIENT', 'EN_ROUTE_HOSPITAL', 'COMPLETED', 'CANCELLED'))
);

CREATE UNIQUE INDEX trips_one_active_per_incident_idx
    ON trips (incident_id)
    WHERE status IN ('RESERVED', 'EN_ROUTE_PATIENT', 'EN_ROUTE_HOSPITAL');

CREATE TABLE dispatch_decisions (
    id VARCHAR(40) PRIMARY KEY,
    incident_id VARCHAR(40) NOT NULL REFERENCES incidents(id),
    ambulance_id VARCHAR(40) NOT NULL REFERENCES ambulances(id),
    hospital_id VARCHAR(40) NOT NULL REFERENCES hospitals(id),
    pickup_eta_minutes DOUBLE PRECISION NOT NULL,
    hospital_eta_minutes DOUBLE PRECISION NOT NULL,
    hospital_load DOUBLE PRECISION NOT NULL,
    quality_penalty DOUBLE PRECISION NOT NULL,
    type_penalty DOUBLE PRECISION NOT NULL,
    total_cost DOUBLE PRECISION NOT NULL,
    explanation TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX dispatch_decisions_incident_idx ON dispatch_decisions (incident_id, created_at DESC);

CREATE TABLE dispatch_candidate_scores (
    id BIGSERIAL PRIMARY KEY,
    dispatch_decision_id VARCHAR(40) NOT NULL REFERENCES dispatch_decisions(id) ON DELETE CASCADE,
    rank INTEGER NOT NULL,
    ambulance_id VARCHAR(40) NOT NULL,
    hospital_id VARCHAR(40) NOT NULL,
    pickup_eta_minutes DOUBLE PRECISION NOT NULL,
    hospital_eta_minutes DOUBLE PRECISION NOT NULL,
    hospital_load DOUBLE PRECISION NOT NULL,
    quality_penalty DOUBLE PRECISION NOT NULL,
    type_penalty DOUBLE PRECISION NOT NULL,
    total_cost DOUBLE PRECISION NOT NULL,
    explanation TEXT NOT NULL,
    UNIQUE (dispatch_decision_id, rank)
);

CREATE TABLE outbox_events (
    id VARCHAR(40) PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(80) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX outbox_unpublished_idx ON outbox_events (created_at) WHERE published_at IS NULL;

INSERT INTO ambulances (id, call_sign, type, status, latitude, longitude, base_station) VALUES
('AMB-101', 'Aster Alpha', 'ALS', 'AVAILABLE', 12.9719, 77.6412, 'Indiranagar'),
('AMB-102', 'Pulse Bravo', 'BLS', 'AVAILABLE', 12.9352, 77.6245, 'Koramangala'),
('AMB-103', 'Rescue Charlie', 'ICU', 'AVAILABLE', 13.0118, 77.5549, 'Malleshwaram'),
('AMB-104', 'Rapid Delta', 'ALS', 'AVAILABLE', 12.9141, 77.6101, 'BTM Layout'),
('AMB-105', 'Care Echo', 'BLS', 'AVAILABLE', 12.9987, 77.5924, 'Hebbal'),
('AMB-106', 'Life Foxtrot', 'ALS', 'OFFLINE', 12.9698, 77.7500, 'Whitefield')
ON CONFLICT (id) DO NOTHING;

INSERT INTO hospitals (id, name, latitude, longitude, total_beds, available_beds, quality_score) VALUES
('HOS-201', 'Narayana Cardiac Centre', 12.9384, 77.6906, 42, 7, 0.94),
('HOS-202', 'Manipal Emergency Hospital', 12.9592, 77.6489, 65, 14, 0.90),
('HOS-203', 'Victoria Trauma Institute', 12.9634, 77.5739, 58, 4, 0.86),
('HOS-204', 'Cloudnine Pediatric ER', 12.9336, 77.6234, 35, 9, 0.89),
('HOS-205', 'Baptist North Care', 13.0358, 77.5891, 44, 11, 0.84)
ON CONFLICT (id) DO NOTHING;

INSERT INTO hospital_specialties (hospital_id, condition) VALUES
('HOS-201', 'CARDIAC'),
('HOS-201', 'STROKE'),
('HOS-202', 'TRAUMA'),
('HOS-202', 'CARDIAC'),
('HOS-202', 'GENERAL'),
('HOS-203', 'TRAUMA'),
('HOS-203', 'GENERAL'),
('HOS-204', 'PEDIATRIC'),
('HOS-204', 'GENERAL'),
('HOS-205', 'STROKE'),
('HOS-205', 'GENERAL')
ON CONFLICT (hospital_id, condition) DO NOTHING;

INSERT INTO incidents (id, requester_user_id, patient_name, phone, condition, priority, latitude, longitude, created_at, status) VALUES
('INC-301', 'patient.demo', 'Ananya Rao', '+91-90000-10001', 'CARDIAC', 'CRITICAL', 12.9458, 77.6309, now() - interval '3 minutes', 'NEW'),
('INC-302', 'patient.demo', 'Rohan Mehta', '+91-90000-10002', 'TRAUMA', 'HIGH', 12.9166, 77.6101, now() - interval '90 seconds', 'NEW')
ON CONFLICT (id) DO NOTHING;
