# LifeLine

LifeLine is an emergency dispatch product prototype for matching an incident to the right ambulance and hospital in one decision.

The product direction is intentionally pragmatic: build a correct, explainable dispatch workflow first, then evolve toward durable data, live tracking, eventing, optimization, and eventually service boundaries.

## Current Version Status

| Version | Status | Purpose |
| --- | --- | --- |
| V1 | Implemented | End-to-end dispatch prototype with seeded Bengaluru data, in-memory state, REST APIs, and React dashboard |
| V2 | Implemented | Durable dispatch foundation with PostgreSQL/PostGIS schema, row-locked reservations, decision audit, outbox records, and candidate explanations |
| V3 | Implemented in this branch | Multi-actor workflow with patient, driver, hospital, and control tower role surfaces plus rerouting actions |

## V1 Implemented Scope

- Java Spring Boot backend
- React + TypeScript dashboard
- Seeded Bengaluru ambulances, hospitals, and incidents
- Weighted dispatch scoring for patient, ambulance, and hospital matching
- REST APIs for incidents, ambulances, hospitals, trips, and metrics
- In-memory store for fastest V1 iteration
- Docker Compose placeholders for PostGIS and Redis

## V2 Implemented Scope

- PostgreSQL/PostGIS-backed operational schema managed by Flyway
- JDBC-backed `LifeLineStore` as the default backend store
- `memory` profile for the original in-memory V1-style local fallback
- Strong reservation commit checks using row locks for incident, ambulance, and hospital state
- Dispatch decision audit table with winning score details
- Candidate score persistence for top dispatch alternatives
- Outbox table and event creation for incident and dispatch lifecycle events
- New read APIs for dispatch decisions and outbox events
- Dashboard display of top dispatch candidates after assignment

## V3 Implemented Scope

- Single React application with role-based surfaces for patient, ambulance driver, hospital, and control tower workflows
- Patient request flow that creates incidents and shows the care journey after dispatch
- Driver workflow for moving trips through pickup, transfer, and arrival states
- Hospital workflow for updating available capacity and marking a hospital exhausted
- Control tower workflow for dispatching incidents, viewing the route map, inspecting events, and triggering reroutes
- Backend APIs for trip status transitions, hospital capacity updates, and rerouting active trips
- Reroute scoring that excludes the current hospital and exhausted hospitals while reusing the dispatch engine's explainable ranking model
- Outbox events for trip status changes, hospital capacity changes, and reroutes
- Frontend timeline combining outbox events and dispatch audit records

## V2 Product Goal

V2 moves LifeLine from a demo-only prototype toward a durable, production-shaped system.

The V2 goal is not to split into microservices yet. The V2 goal is to make the core workflow correct:

1. Persist incidents, ambulances, hospitals, reservations, trips, and decision audit records.
2. Use geospatial data deliberately instead of hard-coded in-memory filtering.
3. Separate dispatch decisions from resource reservations.
4. Record why every assignment was made.
5. Prepare reliable event publishing without adding Kafka too early.
6. Keep the system easy to run locally and easy to explain in a PR/interview.

## V2 Target Architecture

```text
frontend/
  React + TypeScript dispatch console
  Map view, incident queue, dispatch panel, resource state

backend/
  Java 21 + Spring Boot modular monolith

  modules:
    api
      REST boundary and request validation

    intake
      incident creation, triage inputs, incident lifecycle

    dispatch
      candidate search, scoring, decision explanation

    ambulance-registry
      ambulance capability, availability, location, reservation state

    hospital-capacity
      hospital specialty, bed availability, bed reservation state

    trip-tracking
      trip lifecycle, active assignments, tracking-ready model

    audit
      decision snapshots, outbox records, replay-friendly event log

data/
  PostgreSQL + PostGIS
    source of truth for durable operational data

  Redis
    live ambulance locations, short-lived locks, fast availability projections

integration/
  OSRM or GraphHopper
    route ETA provider behind an internal interface

  Transactional outbox
    reliable event handoff before introducing Kafka or Redpanda
```

## V2 Request Flow

```text
1. Dispatcher creates incident
2. Backend validates triage fields and stores incident
3. Dispatch module searches compatible ambulances and hospitals
4. Routing provider estimates pickup and transfer ETA
5. Dispatch module scores candidates and records explanation
6. Reservation workflow reserves ambulance and hospital bed in one transaction boundary
7. Trip is created
8. Audit record and outbox event are written
9. Dashboard refreshes resource and trip state
```

## V3 Product Goal

V3 turns LifeLine from a single dispatch console into a multi-actor emergency coordination workflow.

The V3 goal is to show how the same backend state is experienced by different users:

1. A patient raises the emergency request and sees progress.
2. A driver receives the assigned trip and advances the trip lifecycle.
3. A hospital manages live receiving capacity.
4. A control tower observes the full system, dispatches incidents, and reroutes active trips when capacity changes.

## V3 Target Architecture

```text
frontend/
  React + TypeScript single app

  role routes:
    /patient
      incident intake and patient-facing journey state

    /driver
      assigned trips and trip status transitions

    /hospital
      hospital capacity controls and incoming trip list

    /control
      system map, incident queue, trip selection, decisions, timeline

backend/
  Java 21 + Spring Boot modular monolith

  api
    role workflow endpoints

  dispatch
    initial assignment and alternate-hospital reroute decisions

  store
    PostgreSQL-backed authoritative state changes
    memory profile parity for local demos

data/
  PostgreSQL/PostGIS
    source of truth for incidents, hospitals, ambulances, trips, audit, outbox

  outbox_events
    local event log for workflow visibility before adding a broker
```

## V3 Workflow

```text
1. Patient creates an incident
2. Control tower runs dispatch
3. Backend reserves ambulance and hospital capacity
4. Driver moves trip from reserved to pickup to hospital transfer
5. Hospital updates capacity as receiving conditions change
6. If the matched hospital becomes exhausted, control tower reroutes the active trip
7. Backend scores alternate hospitals, reserves the new hospital, updates the trip, and writes an outbox event
```

## Design Decisions

| ID | Decision | Why |
| --- | --- | --- |
| D01 | Use a modular monolith for V2, not microservices | The workflow is still changing. A modular monolith gives strong boundaries without distributed transaction complexity. |
| D02 | Use Java 21 + Spring Boot for backend | Java is strong for durable backend systems, concurrency, type safety, and enterprise interview credibility. |
| D03 | Keep React + TypeScript for frontend | The dashboard needs a maintainable, interactive operational UI with typed API contracts. |
| D04 | Use PostgreSQL + PostGIS as source of truth in V2 | Dispatch depends on durable incidents, trips, capacity, reservations, and geospatial queries. |
| D05 | Use Redis only for live/ephemeral state | Redis is appropriate for live locations, TTL locks, and fast projections, not legal source-of-truth records. |
| D06 | Treat bed and ambulance reservation as strongly consistent | A stale read is acceptable for candidate discovery, but the final reservation must be authoritative. |
| D07 | Add a transactional outbox before Kafka | It gives reliable event publishing while avoiding premature broker complexity. Kafka can be introduced after event contracts stabilize. |
| D08 | Keep weighted scoring before advanced optimization | Weighted scoring is explainable and testable. Min-cost flow or Hungarian-style batch optimization can come later. |
| D09 | Store dispatch explanations | Emergency dispatch decisions must be debuggable: ETA, capability, specialty, load, and rejection reasons should be preserved. |
| D10 | Hide routing behind an internal interface | V2 can start with a mock ETA provider and later switch to OSRM without changing dispatch logic. |
| D11 | Separate compatibility filters from scoring | Hard constraints reject unsafe choices; scoring ranks valid choices. This avoids hiding clinical constraints inside weights. |
| D12 | Prefer generated/synthetic demo data until data contracts stabilize | Synthetic Bengaluru data lets us test product behavior without depending on messy external datasets early. |
| D13 | Keep service boundaries visible in package/module names | Future extraction into services should be a move operation, not a rewrite. |
| D14 | Add observability as part of V2 design, not as polish | Dispatch latency, reservation failure rate, capacity staleness, and reroute count are core product signals. |
| D15 | Avoid ML triage in V2 | Clinical triage rules must be deterministic and explainable first. ML can be a later assistive layer. |
| D16 | Keep four role experiences inside one frontend app for V3 | The product needs role-specific workflows now, but separate deployments would add friction before auth and tenancy exist. |
| D17 | Use backend state transitions instead of frontend-only simulation | The demo should exercise real trip, capacity, reroute, audit, and outbox behavior. |
| D18 | Reroute active trips through the dispatch engine | Alternate hospital selection must stay explainable and testable rather than becoming a special-case UI shortcut. |
| D19 | Treat hospital capacity updates as authoritative | Hospital UI is the source for live receiving availability; reroute logic must react to exhausted capacity. |
| D20 | Keep eventing local through the outbox in V3 | The product should prove event contracts and timeline behavior before introducing Kafka or another broker. |

## V2 Data Ownership

| Data | Owner Module | V2 Storage |
| --- | --- | --- |
| Incident | intake | PostgreSQL |
| Ambulance profile | ambulance-registry | PostgreSQL |
| Ambulance live location | ambulance-registry | Redis projection, PostgreSQL snapshot optional |
| Hospital profile | hospital-capacity | PostgreSQL |
| Hospital bed availability | hospital-capacity | PostgreSQL authoritative record, Redis projection optional |
| Reservation | dispatch plus resource owner modules | PostgreSQL transaction |
| Trip | trip-tracking | PostgreSQL |
| Dispatch explanation | audit | PostgreSQL |
| Outbox event | audit | PostgreSQL outbox table |

## V2 Consistency Model

Candidate search can use slightly stale read models because it only proposes options.

Final assignment cannot use stale state. The V2 reservation commit must verify:

- Incident is still dispatchable
- Ambulance is still available
- Ambulance capability still satisfies the incident
- Hospital can still treat the condition
- Hospital still has capacity
- Trip is created exactly once for the incident

The reservation operation should be idempotent using a request key or incident-level uniqueness constraint.

## API Surface

Current V1 APIs:

- `GET /api/ambulances`
- `GET /api/hospitals`
- `GET /api/incidents`
- `POST /api/incidents`
- `POST /api/dispatch`
- `GET /api/trips`
- `GET /api/dispatch-decisions`
- `GET /api/outbox-events`
- `GET /api/metrics`
- `POST /api/demo/reset`
- `POST /api/trips/{tripId}/status`
- `POST /api/hospitals/{hospitalId}/capacity`
- `POST /api/trips/{tripId}/reroute`

V2 keeps the original APIs stable where possible, while V3 adds role workflow actions for trips, hospital capacity, and rerouting.

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 20+
- npm 10+

## Run Backend

Start PostgreSQL first:

```powershell
docker compose up -d postgres
```

Then run the backend:

```powershell
cd backend
mvn spring-boot:run
```

Backend starts at `http://localhost:8080`.

Quick check:

```powershell
curl.exe http://localhost:8080/api/metrics
```

To run the fallback in-memory profile instead of PostgreSQL:

```powershell
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=memory
```

## Run Frontend

```powershell
cd frontend
npm install
npm run dev
```

Frontend starts at `http://localhost:5173`.

Role routes:

- `http://localhost:5173/patient`
- `http://localhost:5173/driver`
- `http://localhost:5173/hospital`
- `http://localhost:5173/control`
