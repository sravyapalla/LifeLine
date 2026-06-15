# PR Summary: LifeLine V2 Durable Dispatch Foundation

## Title

Build LifeLine V2 durable dispatch foundation

## Summary

- Add Flyway-managed PostgreSQL/PostGIS schema for durable LifeLine data
- Add JDBC-backed `LifeLineStore` as the default backend persistence layer
- Preserve the V1 in-memory implementation behind a `memory` profile
- Add row-locked reservation checks for incident, ambulance, and hospital state
- Persist dispatch decisions, candidate scores, and outbox events
- Add APIs for dispatch audit records and outbox records
- Improve the dashboard by showing top candidate rankings after dispatch
- Update README and `PLAN.md` with V2 architecture, decisions, and roadmap

## Improvements From V1 To V2

V1 proved the end-to-end dispatch loop with in-memory state and seeded data.

V2 improves the project by implementing the next production-shaped foundation:

- Moves from in-memory state to PostgreSQL/PostGIS-backed operational data
- Separates candidate discovery from authoritative row-locked reservation
- Establishes strong consistency rules for final dispatch assignment in code
- Persists decision explainability through dispatch audit and candidate score tables
- Introduces outbox records before Kafka to avoid premature distributed complexity
- Defines Redis as live/ephemeral state, not source of truth
- Keeps the monolith modular so service extraction remains possible later
- Creates a versioned engineering roadmap through `PLAN.md`

## Design Decisions Added

- Modular monolith first, microservices later
- Java 21 + Spring Boot remains the backend foundation
- PostgreSQL/PostGIS becomes the V2 durable data store
- Redis is reserved for live location, TTL locks, and projections
- Weighted scoring remains the default algorithm before batch optimization
- Routing is hidden behind a provider interface
- Dispatch explanations must be persisted
- Kafka is deferred until outbox contracts stabilize

## Testing

- Passed: `cd frontend && npm run build`
- Blocked in this environment: `cd backend && mvn test`
  - Maven reached the project but timed out resolving new dependencies from Maven Central.
  - Re-run locally with working Maven repository/proxy access.
- Blocked in this environment: `docker compose config`
  - Docker CLI was not installed on this machine.

Backend/manual run commands for local validation:

```powershell
docker compose up -d postgres
cd backend
mvn test
mvn spring-boot:run
```

Frontend/manual run commands:

```powershell
cd frontend
npm install
npm run dev
```

## Follow-Up Work

- Add reservation transaction tests
- Add routing provider interface
- Add Redis-backed live location projection
- Add outbox consumer and projection worker
- Add OSRM/GraphHopper adapter
- Add frontend stale capacity and audit views
