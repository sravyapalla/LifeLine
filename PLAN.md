# LifeLine Engineering Plan

This plan is the living engineering roadmap for LifeLine. It records what we are building, why we are sequencing it this way, and which design decisions are accepted or still open.

## Engineering Principles

1. Correctness before distribution.
2. Explainability before algorithmic cleverness.
3. Durable reservations before live optimization.
4. Stable module boundaries before microservice extraction.
5. Measured improvements before architectural expansion.
6. Local developer experience matters.

## Version Roadmap

| Version | Theme | Outcome |
| --- | --- | --- |
| V1 | End-to-end prototype | In-memory dispatch workflow with React dashboard and seeded data |
| V2 | Durable dispatch foundation | PostgreSQL/PostGIS model, authoritative reservations, audit records, outbox records, candidate explanations |
| V3 | Event-driven reliability | Transactional outbox consumers, async projections, notifications, operational metrics |
| V4 | Optimization and simulation | Greedy versus optimized dispatch comparisons, load simulation, candidate pruning, fairness logic |
| V5 | Production hardening | Auth, RBAC, PII handling, SLOs, resilience testing, deployment pipeline |

## V2 Scope

V2 should convert LifeLine from a memory-backed demo into a durable dispatch foundation.

### V2 Must Have

- PostgreSQL persistence for incidents, ambulances, hospitals, trips, and dispatch audit records
- PostGIS-compatible location indexes and schema foundation
- Clear reservation transaction rules with row-level locks
- Dispatch explanation persisted with each assignment
- Outbox records for incident and dispatch lifecycle events
- README architecture and decision documentation

### V2 Should Have

- Redis-backed live ambulance location projection
- Flyway database migrations
- Repository interfaces separated from controllers and dispatch logic
- Frontend display of decision explanation details
- Metrics names documented for future observability
- Tests for reservation and dispatch edge cases

### V2 Should Not Have

- Kafka as a required runtime dependency
- Microservice split
- ML triage
- Blockchain audit
- Real medical data ingestion

## V2 Implementation Plan

### Phase 1: Persistence Foundation

- Add database dependencies - Done
- Introduce Flyway migrations - Done
- Create tables for incidents, ambulances, hospitals, trips, dispatch decisions, candidate scores, and outbox events - Done
- Replace in-memory store with repository interfaces and database-backed implementation - Done

### Phase 2: Reservation Workflow

- Enforce one active trip per incident - Done
- Verify ambulance and hospital state during commit - Done
- Use row-level locks for final reservation checks - Done
- Add idempotency key or request-level uniqueness constraints
- Add tests for concurrent/duplicate dispatch attempts

### Phase 3: Routing Boundary

- Add `RoutingProvider` interface
- Keep mock ETA provider for local development
- Prepare OSRM/GraphHopper adapter behind the interface
- Keep dispatch scoring independent from routing vendor details

### Phase 4: Audit and Outbox

- Persist candidate score and final explanation - Done
- Add outbox table for incident created and dispatch reserved events - Done
- Add trip created and reservation failed events
- Keep outbox consumer optional until V3

### Phase 5: Dashboard Improvements

- Show winning score breakdown - Done
- Show top candidate alternatives - Done
- Show rejected candidate reasons
- Show reservation status
- Show stale capacity warning once capacity timestamps exist

## Accepted Design Decisions

| ID | Decision | Status |
| --- | --- | --- |
| D01 | Modular monolith first | Accepted |
| D02 | Java 21 + Spring Boot backend | Accepted |
| D03 | React + TypeScript frontend | Accepted |
| D04 | PostgreSQL/PostGIS for V2 source of truth | Accepted |
| D05 | Redis for live/ephemeral state only | Accepted |
| D06 | Strong consistency for final reservation | Accepted |
| D07 | Transactional outbox before Kafka | Accepted |
| D08 | Weighted scoring before batch optimization | Accepted |
| D09 | Persist dispatch explanations | Accepted |
| D10 | Routing hidden behind provider interface | Accepted |
| D11 | Hard compatibility filters before scoring | Accepted |
| D12 | Synthetic data until contracts stabilize | Accepted |
| D13 | Microservices deferred until module boundaries stabilize | Accepted |

## Open Design Questions

| Question | Current Leaning |
| --- | --- |
| Should V2 use JPA, jOOQ, or JDBC templates? | Start with Spring Data JDBC or JPA only if entities stay simple. Avoid ORM complexity in dispatch-critical paths. |
| Should Redis be mandatory in V2? | No. Make Redis optional until durable PostgreSQL flow is complete. |
| Should OSRM be mandatory in V2? | No. Keep mock routing first, add OSRM adapter after database migration. |
| Should reservation be one transaction or saga in V2? | One database transaction in modular monolith. Saga belongs after service extraction. |
| Should V2 introduce auth? | Not yet. V5 should add auth/RBAC after core operational model stabilizes. |

## Metrics To Introduce

- `dispatch_decision_latency_ms`
- `dispatch_candidate_count`
- `dispatch_no_candidate_total`
- `reservation_success_total`
- `reservation_conflict_total`
- `hospital_capacity_staleness_seconds`
- `active_trip_count`
- `ambulance_available_count`

## PR Discipline

Every PR should include:

- What changed
- What improved compared with the previous version
- Design decisions added or changed
- Risks and follow-up work
- Test results
