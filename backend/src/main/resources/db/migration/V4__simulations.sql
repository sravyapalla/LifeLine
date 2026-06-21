CREATE TABLE simulation_runs (
    id VARCHAR(40) PRIMARY KEY,
    request JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE simulation_assignments (
    id BIGSERIAL PRIMARY KEY,
    simulation_id VARCHAR(40) NOT NULL REFERENCES simulation_runs(id) ON DELETE CASCADE,
    strategy VARCHAR(40) NOT NULL,
    incident_id VARCHAR(40) NOT NULL,
    condition VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    incident_latitude DOUBLE PRECISION NOT NULL,
    incident_longitude DOUBLE PRECISION NOT NULL,
    ambulance_id VARCHAR(40),
    hospital_id VARCHAR(40),
    pickup_eta_minutes DOUBLE PRECISION NOT NULL,
    hospital_eta_minutes DOUBLE PRECISION NOT NULL,
    total_cost DOUBLE PRECISION NOT NULL,
    matched BOOLEAN NOT NULL,
    reason TEXT NOT NULL,
    CONSTRAINT simulation_assignments_strategy_chk CHECK (strategy IN ('GREEDY_SEQUENTIAL', 'GLOBAL_MIN_COST')),
    CONSTRAINT simulation_assignments_condition_chk CHECK (condition IN ('CARDIAC', 'TRAUMA', 'PEDIATRIC', 'STROKE', 'GENERAL')),
    CONSTRAINT simulation_assignments_priority_chk CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX simulation_assignments_run_strategy_idx ON simulation_assignments (simulation_id, strategy, id);
