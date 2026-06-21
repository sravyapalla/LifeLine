# LifeLine

LifeLine is an emergency dispatch product prototype for matching an incident to the right ambulance and hospital in one decision.

The product direction is intentionally pragmatic: build a correct, explainable dispatch workflow first, then evolve toward durable data, live tracking, eventing, optimization, and eventually service boundaries.

## Current Version Status

| Version | Status | Purpose |
| --- | --- | --- |
| V1 | Implemented | End-to-end dispatch prototype with seeded Bengaluru data, in-memory state, REST APIs, and React dashboard |
| V2 | Implemented | Durable dispatch foundation with PostgreSQL/PostGIS schema, row-locked reservations, decision audit, outbox records, and candidate explanations |
| V3 | Implemented | Multi-actor workflow with patient, driver, hospital, and control tower role surfaces plus rerouting actions |
| V4 | Implemented | Event-driven reliability foundation with processable outbox events, health summaries, publish metrics, and control tower visibility |
| V5 | Implemented | Ops Simulator with required Docker runtime, Kafka event streaming, Redis live locations, notifications, routing abstraction, and optimization comparisons |
| V6 | Implemented in this branch | Production Trust release with local JWT auth, backend RBAC, least-privilege PII responses, security audit trail, hardened API boundaries, and authenticated role UI |

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

## V4 Implemented Scope

- Store-level pending outbox query and publish operations
- PostgreSQL publish batching using row locks and `SKIP LOCKED`
- Memory profile parity for local outbox publish demos
- Manual backend endpoint to publish pending outbox events
- Optional scheduled outbox publishing behind configuration
- Metrics for pending and published outbox events
- Event health summary with backlog age and event-type counts
- Retry state with publish attempts, last failure reason, and next retry time
- Configurable publisher abstraction to separate event delivery from outbox state management
- Failure-demo publisher mode for exercising retry behavior locally
- Opt-in PostgreSQL/Testcontainers integration test for JDBC outbox retry state
- Control Tower outbox reliability panel with manual publish action
- Timeline visibility for pending versus published event state

## V5 Implemented Scope

- Required Docker runtime for PostgreSQL/PostGIS, Redis, and Kafka
- Routing provider boundary with deterministic straight-line ETA provider
- Redis-backed live ambulance location projection used by dispatch and maps
- Kafka outbox publisher mode using the V4 retry/failure lifecycle
- Role-targeted notification center generated from workflow events
- Simulation run APIs and durable simulation history
- Greedy sequential versus global min-cost optimization comparison
- New `/simulation` route for scenario design, comparison, and playback
- Expanded operational metrics for simulations, live locations, notifications, and Kafka failures

## V6 Implemented Scope

- Stateless local JWT authentication with seeded demo users
- Backend-enforced RBAC for patient, driver, hospital, and control roles
- Incident ownership through `requester_user_id`
- Role-aware incident responses with masked patient PII for driver and hospital users
- Control-only security audit trail for login, denied access, dispatch, trip status, hospital capacity, reroute, outbox publish, simulation, notification ack, and demo reset events
- Configurable CORS allowed origins with `http://localhost:5173` as the local default
- Consistent JSON error responses for API and security failures
- Authenticated React login/logout flow with route gating and signed-in user badge
- Control Tower audit visibility alongside outbox and workflow timeline

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

## V4 Product Goal

V4 begins turning the outbox from an audit table into a reliability mechanism.

The V4 goal is not to add Kafka yet. The goal is to prove the event lifecycle inside the modular monolith:

1. Workflow actions write durable outbox events in the same transaction as state changes.
2. Operators can see which events are still pending.
3. A processor can claim and publish a bounded batch safely.
4. Published events are marked with `published_at`.
5. The Control Tower exposes event health as an operational signal.
6. Event health includes backlog age and event-type distribution, not just raw event rows.
7. Failed delivery attempts are retained with retry state instead of being hidden in logs.

## V4 Target Architecture

```text
backend/
  outbox
    OutboxProcessor
      manual publish endpoint
      optional scheduled polling
      bounded batch size
      retry delay

    OutboxSummaryService
      pending and published counts
      ready, failed, and retry-scheduled counts
      oldest pending backlog age
      event-type breakdown

    OutboxPublisher
      configurable logging/failure demo publisher
      future notification or broker adapter boundary

  store
    pending event query
    claim ready events
    mark publish success or failure
    PostgreSQL row locking with SKIP LOCKED

data/
  outbox_events
    payload
    created_at
    published_at
    publish_attempts
    last_publish_error
    next_publish_attempt_at

frontend/
  control tower
    pending/published metrics
    manual publish action
    backlog health summary
    event timeline state
```

## V4 Outbox Flow

```text
1. Patient, dispatch, driver, hospital, or reroute action changes backend state
2. Backend writes the matching outbox event in the same transaction
3. Event remains pending while published_at is null
4. Manual publish endpoint or optional scheduled processor claims a batch
5. Processor sends each claimed event through the publisher adapter
6. Successful events are marked with `published_at`
7. Failed events retain `last_publish_error` and wait until `next_publish_attempt_at`
8. Summary endpoint reports backlog age, last publish time, retry state, and event-type distribution
9. Control Tower refreshes pending, published, failed, ready, and retry-waiting health state
```

## V5 Product Goal

V5 turns LifeLine from a workflow demo into an operations simulator.

The V5 goal is to let operators stress the system before a real emergency wave happens:

1. Run the full local stack with PostgreSQL/PostGIS, Redis, and Kafka.
2. Move ambulances through live Redis location updates.
3. Compare greedy dispatch with a bounded global optimizer.
4. Simulate hospital exhaustion, ambulance outages, and incident surges.
5. Publish workflow events through Kafka while preserving V4 outbox retry guarantees.
6. Notify patient, driver, hospital, and control roles from published workflow events.
7. Keep routing swappable behind an internal provider before adding OSRM.

## V5 Target Architecture

```text
frontend/
  role routes
    /patient
    /driver
    /hospital
    /control
    /simulation

backend/
  routing
    RoutingProvider
    StraightLineRoutingProvider

  live-location
    Redis-backed ambulance location projection
    memory profile fallback

  outbox
    Kafka publisher mode
    V4 retry state remains authoritative

  notifications
    role-targeted notification inbox
    generated from published outbox events

  simulation
    scenario generator
    greedy sequential strategy
    global min-cost strategy
    persisted run history

data/
  PostgreSQL/PostGIS
    operational source of truth
    simulation run history

  Redis
    live ambulance location snapshots

  Kafka
    outbox event stream
```

## V6 Product Goal

V6 makes LifeLine trustworthy enough to demo as a production-shaped emergency platform.

The V6 goal is not external OAuth or multi-tenant identity yet. The goal is to prove local trust boundaries:

1. Every API request is authenticated except login and health.
2. Role access is enforced by the backend, not by hidden frontend tabs.
3. Patients see their own incidents and full contact data.
4. Drivers and hospitals see only operational incident details for assigned work.
5. Control can inspect the full system, run simulations, publish events, and view audit events.
6. Sensitive actions and denied attempts are recorded in a durable audit trail.

## V6 Target Architecture

```text
frontend/
  login
    JWT stored in local storage
    Authorization: Bearer attached to API calls

  role routes
    /patient      patient-owned incidents and care journey
    /driver       assigned ambulance and trips
    /hospital     scoped hospital capacity and incoming trips
    /control      full operational control plus security audit
    /simulation   control-only ops simulator

backend/
  security
    DemoUserDirectory
    JwtService
    JwtAuthenticationFilter
    SecurityAuditService

  api
    authenticated REST boundary
    role-aware response DTOs
    JSON error contract

  store
    PostgreSQL source of truth
    memory profile parity

data/
  incidents.requester_user_id
    patient ownership boundary

  security_audit_events
    actor, role, action, resource, outcome, reason, metadata, timestamp
```

## V6 Auth Model

Demo users:

| Username | Password | Role | Scope |
| --- | --- | --- | --- |
| `patient.demo` | `lifeline-demo` | `PATIENT` | Own seeded and newly requested incidents |
| `driver.demo` | `lifeline-demo` | `DRIVER` | Ambulance `AMB-101` and assigned trips |
| `hospital.demo` | `lifeline-demo` | `HOSPITAL` | Hospital `HOS-201` and incoming trips |
| `control.demo` | `lifeline-demo` | `CONTROL` | Full operational access |

JWT claims include `sub`, `role`, optional `ambulanceId` or `hospitalId`, `iat`, and `exp`.

Use `LIFELINE_JWT_SECRET` outside local demos. The default secret exists only so the app can run locally without third-party setup.

## V6 RBAC Matrix

| Capability | Patient | Driver | Hospital | Control |
| --- | --- | --- | --- | --- |
| Login and read own profile | Yes | Yes | Yes | Yes |
| Create incident | Yes | No | No | Yes |
| View incidents | Own only | Assigned trips only, masked | Incoming trips only, masked | All |
| View ambulances | Assigned trip only | Own ambulance | Incoming trip only | All |
| Update ambulance location | No | Own ambulance | No | Any |
| View hospitals | Assigned trip only | Assigned trip only | Own hospital | All |
| Update hospital capacity | No | No | Own hospital | Any |
| Dispatch or reroute | No | No | No | Yes |
| Publish outbox | No | No | No | Yes |
| Run simulations | No | No | No | Yes |
| View audit events | No | No | No | Yes |
| Notifications | Own role only | Own role only | Own role only | Any role |

## V6 PII Policy

- Patient and control users receive full patient name and phone for incidents they are allowed to see.
- Driver and hospital users receive operational incident details only.
- Driver and hospital incident views use the incident id as the visible patient label.
- Driver and hospital phone values are masked to the last four digits.
- The internal domain still keeps full incident data so dispatch and audit logic remain consistent; masking happens at the API boundary.

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
| D21 | Process the transactional outbox before adding Kafka | Reliable local event lifecycle should be proven before adding broker operations and deployment complexity. |
| D22 | Keep automatic outbox publishing disabled by default | Local demos should be deterministic; teams can enable scheduled publishing with configuration when they are ready. |
| D23 | Use `SKIP LOCKED` for PostgreSQL outbox batches | Future workers should be able to claim pending events concurrently without double-publishing the same row. |
| D24 | Expose outbox health through product UI, not logs only | Operators should see backlog age, last publish time, and event-type pressure directly inside the Control Tower. |
| D25 | Record outbox retry state before adding external delivery adapters | Attempts, last failure reason, and next retry time make failures debuggable before Kafka, notifications, or webhooks are introduced. |
| D26 | Keep external delivery adapters out of V4 runtime | V4 should prove claim, retry, and observability contracts first. Notification, Redis, and broker adapters can build on this in V5+ without changing the outbox core. |
| D27 | Make V5 a required Docker runtime release | Redis and Kafka behavior should be exercised as real infrastructure in V5, while memory profile remains a lightweight fallback. |
| D28 | Add routing as a provider boundary before OSRM | The dispatch engine should depend on route estimates, not a specific routing vendor. |
| D29 | Keep simulation deterministic with explicit random seeds | Operators and reviewers should be able to replay the same incident surge and compare strategies repeatably. |
| D30 | Compare greedy and global optimization side by side | V5 should teach why optimization matters without replacing the explainable greedy workflow prematurely. |
| D31 | Use local JWT auth for V6 instead of external OAuth | The demo remains self-contained while leaving external providers plug-in-ready for a later version. |
| D32 | Enforce RBAC in the backend | Frontend route gating improves UX, but the API must be the trust boundary. |
| D33 | Mask PII at response mapping time | Dispatch can keep rich internal data while role-specific API DTOs enforce least privilege. |
| D34 | Persist security audit events | Production trust requires an inspectable record of allowed and denied sensitive actions. |
| D35 | Keep control-only operations explicit | Dispatch, reroute, simulation, outbox publishing, and audit views carry operational risk and should be centralized. |
| D36 | Use configurable CORS origins | Local demos should work by default, but wildcard CORS should not be part of the trusted runtime. |

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

Current APIs:

- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET /api/ambulances`
- `GET /api/hospitals`
- `GET /api/incidents`
- `POST /api/incidents`
- `POST /api/dispatch`
- `GET /api/trips`
- `GET /api/dispatch-decisions`
- `GET /api/outbox-events`
- `GET /api/outbox-events/pending`
- `GET /api/outbox-events/summary`
- `POST /api/outbox-events/publish`
- `GET /api/metrics`
- `POST /api/demo/reset`
- `POST /api/trips/{tripId}/status`
- `POST /api/hospitals/{hospitalId}/capacity`
- `POST /api/trips/{tripId}/reroute`
- `POST /api/ambulances/{id}/location`
- `GET /api/ambulance-locations`
- `GET /api/notifications?role={role}`
- `POST /api/notifications/{id}/ack`
- `POST /api/simulations`
- `GET /api/simulations`
- `GET /api/simulations/{id}`
- `GET /api/audit-events?limit={limit}`

V2 keeps the original APIs stable where possible, V3 adds role workflow actions for trips, hospital capacity, and rerouting, V4 adds outbox processing and health operations, V5 adds live location, notifications, and simulation APIs, and V6 adds authentication, role-scoped responses, and audit events. The V4/V5/V6 publish response reports `published`, `failed`, and remaining `pending` events.

## V4/V5/V6 Runtime Configuration

V4 introduced the local logging/failure adapters for retry demos:

```yaml
lifeline:
  outbox:
    publisher:
      mode: logging
```

For local retry demos, start the backend with a failing publisher:

```powershell
cd backend
mvn.cmd spring-boot:run "-Dspring-boot.run.profiles=memory" "-Dspring-boot.run.arguments=--lifeline.outbox.publisher.mode=fail-all --lifeline.outbox.publisher.failure-message=demo-adapter-down"
```

The publisher modes are:

- `logging`: records a successful local publish in backend logs
- `fail-all`: fails every publish attempt
- `fail-event-type`: fails only events matching `lifeline.outbox.publisher.fail-event-type`
- `kafka`: publishes `OutboxEventEnvelope` JSON to Kafka topic `lifeline.outbox.events`

V5 full runtime defaults to Kafka publishing and Redis live-location projection:

```yaml
lifeline:
  outbox:
    publisher:
      mode: kafka
  kafka:
    outbox-topic: lifeline.outbox.events
    publish-timeout-seconds: 5
  live-location:
    ttl-seconds: 180
```

The `memory` Spring profile remains available for lightweight demos and tests. It keeps outbox publishing in logging mode and uses in-memory live ambulance locations instead of Redis.

V6 auth and CORS defaults:

```yaml
lifeline:
  security:
    jwt-secret: ${LIFELINE_JWT_SECRET:dev-only-local-lifeline-secret-change-me}
    token-ttl-minutes: 480
  cors:
    allowed-origins: ${LIFELINE_ALLOWED_ORIGINS:http://localhost:5173}
```

V5 simulation rules:

- `GREEDY_SEQUENTIAL` mirrors the current dispatch behavior.
- `GLOBAL_MIN_COST` performs bounded exact optimization for up to 12 incidents.
- Requests with `strategy=GLOBAL_MIN_COST` and more than 12 incidents are rejected.
- Simulation runs and assignments are persisted in `simulation_runs` and `simulation_assignments`.

The PostgreSQL outbox integration test is opt-in because it requires Docker:

```powershell
cd backend
mvn.cmd test "-Dlifeline.integration.postgres=true" "-Dtest=JdbcOutboxIntegrationTest"
```

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 20+
- npm 10+
- Docker Desktop or a compatible Docker runtime for the full V5 stack

## Run Backend

Start the V5 runtime first:

```powershell
docker compose up -d postgres redis kafka
```

Then run the backend:

```powershell
cd backend
mvn spring-boot:run
```

Backend starts at `http://localhost:8080`.

Quick check:

```powershell
$login = curl.exe -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"control.demo\",\"password\":\"lifeline-demo\"}" | ConvertFrom-Json
curl.exe -H "Authorization: Bearer $($login.token)" http://localhost:8080/api/metrics
```

To run the fallback in-memory profile instead of PostgreSQL:

```powershell
cd backend
mvn.cmd spring-boot:run "-Dspring-boot.run.profiles=memory"
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
- `http://localhost:5173/simulation`

The app opens with the login screen. Use one of the demo users from the V6 auth table above.
