# LifeLine

LifeLine is a production-shaped emergency response platform that coordinates patients, ambulances, hospitals, and operations teams during time-critical incidents.

The product has two jobs:

1. Help a patient request emergency help and track the response.
2. Help operations choose the safest ambulance and receiving hospital using live resource state, auditable decisions, and explainable optimization.

The current implementation is a working full-stack system with authenticated role experiences, dispatch and reroute workflows, Redis live ambulance locations, Kafka-backed outbox publishing, notifications, audit events, and a simulation workspace for surge planning.

## Product Surfaces

LifeLine is intentionally role-specific. The four operational users should not see the same dashboard with tabs hidden by CSS. Each role has a different job, different risk profile, and different data visibility.

| Role | Primary Questions | Product Surface |
| --- | --- | --- |
| Patient | Can I request help, see available coverage, and track my ambulance? | Intake, availability snapshot, request timeline, trip tracking |
| Driver | What patient request am I assigned to, where do I go, and which hospital is ready? | Assignment queue, route state, hospital receiving options, trip status actions |
| Hospital | What is incoming, what capacity do we have, and when should we declare exhaustion? | Incoming ambulance board, capacity controls, clinical load indicators |
| Control | What is happening across the city and what needs intervention? | Incident queue, dispatch, reroute, live map, reliability, audit, simulation |

## Architecture Strategy: Modular Monolith First, Microservices Ready

LifeLine intentionally starts as a **modular monolith** for the core emergency workflow, while keeping clear bounded contexts and deployable service shells for a later microservices migration.

This is a deliberate system design decision, not a shortcut. Emergency dispatch requires strong consistency across incident assignment, ambulance reservation, hospital bed reservation, trip creation, audit logging, and outbox event creation. Keeping that workflow inside one transactional backend avoids premature distributed transactions while the domain model is still evolving.

The system is still **microservices-ready**:

- the code is organized around bounded contexts;
- the gateway is already the browser-facing API boundary;
- outbox events are already published through Kafka;
- Redis is already isolated for live ambulance projections;
- service shells exist for future extraction;
- deployment manifests already model separate runtime components.

The intended migration path is a **strangler pattern**: keep the operations backend as the source-compatible system of record, then extract services only when their data ownership, scaling needs, and API contracts are stable.

### Why Not Pure Microservices First?

| Concern | Microservices First Cost | LifeLine Decision |
| --- | --- | --- |
| Dispatch reservation | Requires distributed consistency across incident, ambulance, hospital, trip, audit, and events | Keep reservation transactional in the operations backend |
| Domain volatility | Early service contracts would churn as product flows change | Stabilize bounded contexts first |
| Local development | Many services, brokers, databases, and network failure modes become mandatory | Keep one productive backend loop plus optional full runtime |
| Team size | Microservices increase operational load before the product is proven | Defer extraction until ownership is clear |
| Reliability | Retries, idempotency, sagas, DLQs, tracing, and versioned APIs become required immediately | Add these patterns incrementally around real workflows |

### Where Microservices Make Sense Later

| Candidate Service | Extraction Trigger |
| --- | --- |
| Live location ingestion | High-frequency GPS writes need independent scaling and retention |
| Notification delivery | SMS/email/push providers need async retries and provider isolation |
| Simulation | Surge planning becomes compute-heavy and should not contend with live dispatch |
| Audit | Immutable audit retention and compliance policies diverge from operational data |
| Routing | OSRM/GraphHopper or vendor routing needs separate caching, limits, and failover |
| Resource management | Hospital capacity and fleet state APIs stabilize and need separate ownership |

## System Architecture

LifeLine currently runs as a modular operations backend behind a gateway, with supporting infrastructure and future service boundaries. The gateway and service shells make the target architecture visible without forcing premature extraction.

```text
Browser
  React + TypeScript role app
    /patient
    /driver
    /hospital
    /control
    /simulation
        |
        v
Gateway Service
  Auth header forwarding
  CORS boundary
  Service discovery endpoint
  API routing
        |
        v
Operations Backend
  Modular monolith with bounded contexts
    Auth and Trust
    Incident
    Resource
    Dispatch
    Trip
    Notification
    Audit
    Simulation
        |
        v
Event and Data Plane
  PostgreSQL/PostGIS  Redis  Kafka
        |
        v
Future Extracted Services
  Incident  Resource  Dispatch  Notification  Audit  Simulation
```

### Bounded Contexts

| Context | Owns | Why It Is A Boundary |
| --- | --- | --- |
| Gateway | Public API routing, CORS, frontend-facing service registry | Keeps browsers away from internal service topology and gives one place for edge policy |
| Auth and Trust | JWT verification, user context, RBAC, audit decisions | Trust rules must be consistent and centrally testable |
| Incident | Patient requests, triage payloads, incident ownership | Intake changes frequently and carries PII risk |
| Resource | Ambulance fleet state, live locations, hospital capacity | Resource state has high read pressure and mixed consistency needs |
| Dispatch | Candidate search, scoring, reservation, reroute, routing provider | Matching logic is the domain core and should stay explainable |
| Trip | Pickup, transfer, completion state | Trip lifecycle is operational state used by patient, driver, hospital, and control |
| Notification | Role-targeted messages and acknowledgement | Delivery can later expand to SMS, email, push, or websockets |
| Audit | Security and workflow audit events | Immutable trust records should not be coupled to UI concerns |
| Simulation | Surge generation, optimizer comparison, replay history | Planning workloads are heavy and should not interfere with live dispatch |

The current implementation keeps these contexts inside the operations backend. The service shells in `services/` are deployable placeholders for the future target architecture, not a claim that every context is already independently owned.

### Service Extraction Roadmap

| Phase | Goal | Result |
| --- | --- | --- |
| 1. Modular monolith | Keep emergency reservation workflows strongly consistent | Fast iteration and fewer distributed failure modes |
| 2. Gateway boundary | Make one browser-facing API and hide internal topology | Frontend remains stable during backend evolution |
| 3. Event contracts | Publish durable workflow events from the outbox | Other services can subscribe without coupling to transactions |
| 4. Read-model extraction | Move notifications, audit views, and simulation history first | Low-risk services leave the monolith before core writes |
| 5. Resource extraction | Move ambulance, hospital, live location, and capacity APIs | Resource scaling separates from dispatch logic |
| 6. Dispatch extraction | Move scoring/reservation only after saga/idempotency patterns are mature | Core matching can scale independently without losing correctness |

## Product Flows

### Hospital Enrollment

Hospitals can submit an application from the login screen without a demo account. The application captures hospital identity, contact information, address, coordinates, specialties, and bed count. Control reviews pending applications and approval creates a real hospital resource that participates in capacity visibility and future dispatch matching.

This keeps public enrollment separate from operational access: submitting an application is unauthenticated, but review and approval are control-only actions.

### Patient Address Intake

Patient incident creation is address-first. The patient enters an address and optional landmark, then can use browser location to attach GPS coordinates. Coordinates remain visible as an operational fallback because dispatch still requires deterministic latitude and longitude until an external geocoder is introduced.

### Live Location Tracking

Drivers can start browser GPS sharing from their driver workspace. Each GPS update writes to the ambulance live-location projection, which is stored in Redis with TTL in the full runtime and falls back to memory for lightweight demos. Dispatch and maps use live ambulance location when fresh, otherwise they fall back to the persisted fleet location.

### Active Ambulance Visibility

Control Tower now separates active ambulances from the full fleet. A unit is active when it has a fresh live location or is attached to an active trip. This gives operators a clearer answer to which ambulances are currently moving or committed, instead of asking them to infer it from raw fleet state.

## Data Architecture

| Data | System of Record | Notes |
| --- | --- | --- |
| Incidents | PostgreSQL/PostGIS | Durable emergency records with requester ownership |
| Ambulance profile | PostgreSQL/PostGIS | Fleet identity, capability, base location, operational status |
| Ambulance live location | Redis with TTL | Fast, ephemeral projection; dispatch falls back to persisted location |
| Hospital profile and capacity | PostgreSQL/PostGIS | Bed availability is authoritative and auditable |
| Hospital applications | PostgreSQL/PostGIS or memory profile | Public partner onboarding queue reviewed by control |
| Trips | PostgreSQL/PostGIS | Assignment and care journey lifecycle |
| Dispatch decisions | PostgreSQL/PostGIS | Candidate scores, winning reason, alternatives |
| Outbox events | PostgreSQL/PostGIS, then Kafka | Transactional outbox protects state change and event publishing |
| Notifications | PostgreSQL/PostGIS or memory profile | Role-targeted read model generated from workflow events |
| Security audit | PostgreSQL/PostGIS or memory profile | Login, denied access, dispatch, reroute, capacity, publish, simulation, ack |
| Simulation runs | PostgreSQL/PostGIS or memory profile | Deterministic scenario history and strategy comparison |

### Consistency Model

LifeLine uses different consistency models for different types of data:

| Workflow | Consistency Requirement | Design |
| --- | --- | --- |
| Dispatch reservation | Strong consistency | Incident status, ambulance reservation, hospital bed decrement, trip creation, dispatch audit, and outbox insert happen in one transaction |
| Hospital capacity updates | Strong consistency | Capacity is authoritative in PostgreSQL and audited |
| Live ambulance location | Eventual freshness | Redis stores short-lived location snapshots with TTL; dispatch falls back to persisted fleet location |
| Notifications | Eventually consistent | Generated from workflow events and acknowledged independently |
| Audit | Append-first trust record | Security and workflow decisions are recorded as immutable audit events |
| Simulation | Isolated planning data | Scenario runs do not mutate operational resources |

The design avoids pretending every state update has the same consistency needs. Durable operational decisions use PostgreSQL transactions. High-frequency live movement uses Redis. Cross-boundary communication uses outbox events.

### Core Transaction Boundary

The most important transaction is dispatch:

```text
1. Lock incident.
2. Lock selected ambulance.
3. Lock selected hospital.
4. Validate incident is still dispatchable.
5. Validate ambulance is still available and capable.
6. Validate hospital can treat the condition and has capacity.
7. Reserve ambulance.
8. Decrement hospital bed availability.
9. Mark incident assigned.
10. Create trip.
11. Store dispatch decision and candidate scores.
12. Insert outbox event.
13. Commit.
```

This is why the dispatch core stays in the modular monolith until service extraction has mature saga, idempotency, and reconciliation patterns.

## Dispatch Design

Dispatch is built around explicit hard constraints followed by explainable scoring.

Hard constraints reject unsafe options:

- Ambulance must be available or assigned to the allowed trip for reroute.
- Ambulance capability must support the incident condition.
- Hospital must support the incident condition.
- Hospital must have available capacity.
- Incident must still be dispatchable.

Scoring ranks valid options:

- Pickup ETA
- Transfer ETA
- Incident priority
- Hospital load
- Ambulance capability fit
- Reroute penalty when applicable

The dispatch result stores the winning assignment and the top alternatives so operators can explain why a decision was made.

## Optimization Design

LifeLine has two optimization modes:

| Strategy | Purpose |
| --- | --- |
| `GREEDY_SEQUENTIAL` | Mirrors real-time dispatch where incidents are matched one by one |
| `GLOBAL_MIN_COST` | Compares a small batch of incidents against a globally optimal assignment |

The global optimizer is capped for exact comparison. That keeps it deterministic, explainable, and safe for demos. The simulator reports matched count, unmatched count, average pickup ETA, average transfer ETA, total cost, and improvement percentage.

## Security Model

LifeLine separates **identity** from **application authorization**.

An identity provider answers: who is this person?

LifeLine answers: what role, approval status, hospital, ambulance, and data scope does this person have?

The current runnable implementation uses local JWT authentication so the project works without third-party setup. The production direction is OAuth/OIDC with Google, Microsoft, Auth0, Clerk, Firebase, or another managed identity provider. OAuth should map into LifeLine-owned user profiles rather than replacing LifeLine authorization.

```text
OAuth/OIDC Provider
  email, subject, verified identity
        |
        v
LifeLine User Profile
  role, approval status, ambulance scope, hospital scope
        |
        v
Backend RBAC
  API authorization, PII masking, audit
```

### Signup And Approval Model

| User Type | Signup | Approval | Operational Scope |
| --- | --- | --- | --- |
| Patient | Self-service | Immediate | Own incidents, own trips, own notifications |
| Driver | Self-service application | Control approval required | Assigned ambulance and assigned trips |
| Hospital | Hospital enrollment | Control approval required | Own hospital capacity and incoming trips |
| Control | Invite/admin provisioned only | Admin controlled | Full operational and audit access |

This prevents public signup from creating privileged operational users. A driver or hospital can authenticate before approval, but should not receive operational access until Control approves the profile.

### Local Development Users

For local development, set `LIFELINE_DEMO_PASSWORD` and use the seeded accounts below. These users are a development convenience only; the product model is signup plus approval for operational roles.

| Username | Role | Scope |
| --- | --- | --- |
| `patient.demo` | `PATIENT` | Own seeded and newly requested incidents |
| `driver.demo` | `DRIVER` | Ambulance `AMB-101` and assigned trips |
| `hospital.demo` | `HOSPITAL` | Hospital `HOS-201` and incoming trips |
| `control.demo` | `CONTROL` | Full operational access |

JWT claims include `sub`, `role`, optional `ambulanceId`, optional `hospitalId`, `iat`, and `exp`.

Production OAuth would add or map claims such as provider, provider subject, verified email, and organization membership, while keeping role and resource scope owned by LifeLine.

### RBAC Matrix

| Capability | Patient | Driver | Hospital | Control |
| --- | --- | --- | --- | --- |
| Login and read own profile | Yes | Yes | Yes | Yes |
| Create incident | Yes | No | No | Yes |
| View incidents | Own only | Assigned only, masked | Incoming only, masked | All |
| View ambulances | Assigned trip only | Own ambulance | Incoming trip only | All |
| Update ambulance location | No | Own ambulance | No | Any |
| View hospitals | Assigned trip only | Assigned trip only | Own hospital | All |
| Update hospital capacity | No | No | Own hospital | Any |
| Dispatch or reroute | No | No | No | Yes |
| Publish outbox | No | No | No | Yes |
| Run simulations | No | No | No | Yes |
| View audit events | No | No | No | Yes |
| Notifications | Own role only | Own role only | Own role only | Any role |

### PII Policy

- Patient and control users can see full patient data for incidents they are allowed to view.
- Drivers and hospitals see operational incident details only.
- Driver and hospital responses mask phone numbers and replace patient name with incident identity where possible.
- Internal domain objects retain full data for dispatch and audit; masking happens at the API boundary.

## Eventing And Reliability

LifeLine uses a transactional outbox before Kafka publishing:

```text
1. A workflow action changes durable state.
2. The same transaction writes an outbox event.
3. A publisher claims pending events.
4. Successful publishes mark `published_at`.
5. Failed publishes record attempts, reason, and next retry time.
6. Notifications and timelines are generated from published workflow events.
```

This design avoids losing events when the database succeeds but the broker or downstream service is temporarily unavailable.

### Event Contracts

Current and planned events:

| Event | Producer | Consumers |
| --- | --- | --- |
| `incident.created` | Incident workflow | Notification, control timeline, future incident service read models |
| `dispatch.assigned` | Dispatch workflow | Patient tracking, driver assignment, hospital receiving board |
| `trip.status.updated` | Trip workflow | Patient tracking, hospital board, audit/timeline |
| `trip.rerouted` | Dispatch workflow | Driver, hospital, control, notification |
| `hospital.capacity.updated` | Resource workflow | Control, dispatch eligibility, simulation baselines |
| `ambulance.location.updated` | Driver/resource workflow | Maps, dispatch ETA, active ambulance projection |
| `hospital.application.submitted` | Onboarding workflow | Control approval queue |
| `hospital.application.approved` | Control workflow | Resource catalog, audit, notification |

### Reliability Patterns

| Pattern | Current State | Next Hardening Step |
| --- | --- | --- |
| Transactional outbox | Implemented | Add consumer inbox table for exactly-once consumer effects |
| Retry state | Implemented for publisher attempts | Add dead-letter topic policy and operator replay |
| Idempotency | Partially implicit through resource state checks | Add explicit `Idempotency-Key` for create incident, dispatch, and trip status updates |
| Correlation IDs | Not yet first-class | Add `X-Correlation-Id` at gateway and propagate through logs, audit, and events |
| Routing failover | Deterministic provider exists | Add circuit breaker and cached fallback around OSRM/GraphHopper later |
| Backpressure | Not yet explicit | Add rate limits for public signup, incident creation, and GPS updates |
| Reconciliation | Manual through visible state | Add scheduled checks for stuck trips, stale live locations, and pending outbox age |

### Failure Mode Thinking

| Failure | Expected Behavior |
| --- | --- |
| Kafka unavailable | Core transaction still commits; outbox event remains pending with retry state |
| Redis unavailable | Dispatch and map views fall back to persisted ambulance locations |
| Hospital capacity changes during dispatch | Transaction lock and validation prevent over-reservation |
| Ambulance becomes unavailable during dispatch | Transaction validation rejects the candidate |
| Routing provider unavailable | Deterministic straight-line provider remains a fallback |
| Notification delivery fails | Operational workflow is not blocked; notification can retry asynchronously |
| Duplicate client request | Future idempotency key should return the original result instead of duplicating work |

## Runtime Components

| Component | Default Port | Purpose |
| --- | --- | --- |
| Frontend | `5173` | React role app |
| Gateway service | `8088` | Browser-facing API entry point |
| Operations service | `8080` | Current authenticated workflow API |
| Incident service | `8091` | Incident boundary scaffold |
| Resource service | `8092` | Fleet and hospital boundary scaffold |
| Dispatch service | `8093` | Matching boundary scaffold |
| Notification service | `8094` | Notification boundary scaffold |
| Audit service | `8095` | Audit boundary scaffold |
| Simulation service | `8096` | Simulation boundary scaffold |
| PostgreSQL/PostGIS | `5432` | Durable operational data |
| Redis | `6379` | Live ambulance locations |
| Kafka | `9092` | Outbox event stream |

## Repository Layout

```text
backend/                 Operations service with authenticated workflow APIs
frontend/                React role app for patient, driver, hospital, control, and simulation
services/                Gateway plus bounded-context service shells
deploy/k8s/              Kubernetes deployment baseline
scripts/verify.ps1       Local verification entry point
docker-compose.yml       Full local distributed runtime
```

## Local Development

### Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 20+
- npm 10+
- Docker Desktop or a compatible Docker runtime for the full stack

### Full Local Runtime

```powershell
cd C:\Users\sravya\LifeLine
Copy-Item .env.example .env
```

Edit `.env` and replace every `replace-with-*` value. Then start the full distributed runtime:

```powershell
docker compose up --build
```

The frontend is available at `http://localhost:5173` and calls the gateway at `http://localhost:8088/api`.

For a faster backend-only loop, start only infrastructure:

```powershell
docker compose up -d postgres redis kafka
```

Then run the operations backend on the host:

```powershell
cd backend
$env:LIFELINE_JWT_SECRET="replace-with-a-long-local-secret"
$env:LIFELINE_DEMO_PASSWORD="choose-a-local-demo-password"
mvn.cmd spring-boot:run
```

Run the frontend:

```powershell
cd frontend
npm install
$env:VITE_API_BASE_URL="http://localhost:8080/api"
npm run dev
```

Open `http://localhost:5173`.

### Memory Profile

The memory profile is useful for lightweight demos and tests when Docker is not available:

```powershell
cd backend
$env:LIFELINE_DEMO_PASSWORD="choose-a-local-demo-password"
mvn.cmd spring-boot:run "-Dspring-boot.run.profiles=memory"
```

The memory profile keeps the outbox publisher in logging mode and uses an in-memory live-location projection instead of Redis.

## API Surface

### Auth

- `POST /api/auth/login`
- `POST /api/auth/signup`
- `GET /api/auth/me`

### Operations

- `GET /api/ambulances`
- `POST /api/ambulances/{id}/location`
- `GET /api/ambulance-locations`
- `GET /api/hospitals`
- `POST /api/hospitals/{hospitalId}/capacity`
- `POST /api/hospital-applications`
- `GET /api/hospital-applications`
- `POST /api/hospital-applications/{applicationId}/approve`
- `GET /api/incidents`
- `POST /api/incidents`
- `GET /api/trips`
- `POST /api/trips/{tripId}/status`
- `POST /api/trips/{tripId}/reroute`
- `POST /api/dispatch`

### Reliability And Planning

- `GET /api/dispatch-decisions`
- `GET /api/outbox-events`
- `GET /api/outbox-events/pending`
- `GET /api/outbox-events/summary`
- `POST /api/outbox-events/publish`
- `GET /api/notifications?role={role}`
- `POST /api/notifications/{id}/ack`
- `POST /api/simulations`
- `GET /api/simulations`
- `GET /api/simulations/{id}`
- `GET /api/audit-events?limit={limit}`
- `GET /api/metrics`
- `POST /api/demo/reset`

### Platform

- `GET /api/platform/services`
- `GET /actuator/health`

## Deployment Direction

LifeLine targets a production-ready local and container deployment shape:

- Docker Compose for full local orchestration.
- A gateway service as the only browser-facing API.
- A modular operations backend with separate deployable service shells for the future target architecture.
- Kubernetes manifests for a cloud-ready baseline.
- Environment-driven secrets.
- Health endpoints for every service.
- PostgreSQL, Redis, and Kafka as explicit infrastructure dependencies.

### Environment Strategy

| Environment | Purpose | Runtime Shape |
| --- | --- | --- |
| Memory profile | Fast backend tests and lightweight demos | In-memory store, logging outbox publisher, no Docker required |
| Local full stack | End-to-end development | Docker Compose with PostgreSQL/PostGIS, Redis, Kafka, gateway, backend, frontend |
| Staging | Production-like validation | Container images, managed or containerized dependencies, real secrets, seeded test data |
| Production | Real operations | Managed database/cache/broker, OAuth/OIDC, TLS, observability, backups, rollback |

### Production Hardening Roadmap

| Area | Next Improvement |
| --- | --- |
| Secrets | Move from `.env` and Kubernetes example secrets to a platform secret manager |
| Images | Use immutable image tags instead of `latest` |
| Migrations | Run Flyway as an explicit deployment step or init job |
| Observability | Add structured logs, tracing, correlation IDs, and dashboard-ready metrics |
| Ingress | Add TLS termination, WAF/rate limits, and CORS by environment |
| Resilience | Add circuit breakers, retry budgets, and dead-letter event handling |
| Backups | Define PostgreSQL backup, restore, and retention policy |
| Rollback | Document app rollback and database migration rollback strategy |

### Kubernetes

Kubernetes manifests live in `deploy/k8s/base`.

```powershell
kubectl apply -f deploy/k8s/base/namespace.yaml
kubectl apply -f deploy/k8s/base/configmap.yaml
kubectl apply -f deploy/k8s/base/secrets.example.yaml
kubectl apply -f deploy/k8s/base/operations-service.yaml
kubectl apply -f deploy/k8s/base/boundary-services.yaml
kubectl apply -f deploy/k8s/base/gateway-service.yaml
kubectl apply -f deploy/k8s/base/frontend.yaml
```

Replace `secrets.example.yaml` values or supply an equivalent `lifeline-secrets` object from your secret manager before deploying.

Recommended production hardening beyond this repository:

- Replace local JWT demo auth with managed OAuth/OIDC.
- Move secrets to a platform secret manager.
- Add TLS termination at an ingress controller.
- Add OpenTelemetry tracing across gateway and services.
- Use managed PostgreSQL, Redis, and Kafka.
- Add websocket or SSE push for live trip updates.
- Add OSRM or GraphHopper behind the routing provider.
- Add tenant and organization boundaries for real hospital networks.

## Design Decisions

| ID | Decision | Reasoning |
| --- | --- | --- |
| D01 | Java 21 and Spring Boot for backend services | Strong typing, mature security, transactional data access, and enterprise deployment credibility |
| D02 | React and TypeScript for the frontend | The UI needs typed contracts, rich maps, simulation controls, and role-specific workflows |
| D03 | PostgreSQL/PostGIS as the source of truth | Incidents, ambulances, hospitals, trips, audit, and simulation runs are durable geospatial records |
| D04 | Redis only for live projections | Ambulance movement is high-frequency and ephemeral, so TTL state belongs outside durable records |
| D05 | Kafka behind a transactional outbox | State changes must not be lost when event publishing fails |
| D06 | Modular monolith first | Dispatch reservation needs strong consistency while the domain is still changing |
| D07 | Gateway first, services later | Browsers should depend on one public API boundary while internals evolve |
| D08 | Strangler migration over big-bang rewrite | Service extraction should preserve behavior while contracts mature |
| D09 | Backend-enforced RBAC | The API is the trust boundary; frontend route hiding is only UX |
| D10 | PII masking at response mapping | Domain logic keeps needed data while each role receives the least data required |
| D11 | Audit allowed and denied actions | Trust requires visibility into both successful sensitive actions and blocked attempts |
| D12 | Routing provider abstraction | Dispatch should depend on route estimates, not a specific vendor or engine |
| D13 | Explainable dispatch before opaque ML | Emergency workflows need deterministic, auditable decisions before assistive prediction |
| D14 | Global optimization as simulator comparison | Batch optimization teaches tradeoffs without replacing real-time dispatch prematurely |
| D15 | Role-specific UI surfaces | A patient, driver, hospital operator, and control tower need different mental models and workflows |
| D16 | Environment-driven secrets | Demo convenience must not require committed credentials |
| D17 | Health endpoints everywhere | Distributed systems need fast runtime inspection and deployability checks |
| D18 | OAuth maps to app profiles | Identity provider claims should not replace LifeLine role, approval, and resource-scope policy |
| D19 | Approval for operational roles | Driver, hospital, and control access can affect emergency operations and must not be self-granted |
| D20 | Simulation isolated from live operations | Planning scenarios should explain tradeoffs without mutating real operational resources |

### System Design Tradeoffs

| Tradeoff | Choice | Why |
| --- | --- | --- |
| Strong consistency vs availability during dispatch | Prefer consistency | Over-reserving an ambulance or hospital bed is worse than rejecting and retrying a dispatch |
| Real-time GPS durability | Prefer ephemeral Redis projection | Live location is valuable when fresh and misleading when stale |
| Immediate microservices vs modular monolith | Prefer modular monolith now | The core workflow is transaction-heavy and still evolving |
| External auth now vs self-contained runtime | Keep local auth with OAuth-ready boundary | The project remains runnable while preserving the correct production direction |
| Algorithmic optimality vs explainability | Prefer explainable deterministic scoring | Operators need to understand and defend emergency decisions |
| UI reuse vs role-specific UX | Prefer role-specific UX | Each role has different risk, urgency, and data visibility needs |

### Future Architecture Work

The highest-value future improvements are:

1. Add correlation IDs through gateway, backend logs, audit events, and Kafka messages.
2. Add idempotency keys for incident creation, dispatch, trip status updates, and hospital capacity updates.
3. Add consumer inbox tables before event consumers mutate durable state.
4. Replace local auth with OAuth/OIDC while preserving LifeLine-owned profiles and RBAC.
5. Extract notification and audit read models first because they are lower-risk consumers of workflow events.
6. Add OSRM or GraphHopper behind the routing provider with circuit breaker fallback.
7. Add service-level dashboards for outbox age, publish failures, GPS freshness, dispatch latency, and hospital exhaustion.
8. Add tenant/organization boundaries for multi-hospital networks and ambulance operators.

## Testing

Backend:

```powershell
cd backend
mvn.cmd test
```

Frontend:

```powershell
cd frontend
npm.cmd run build
```

Full local verification:

```powershell
powershell.exe -ExecutionPolicy Bypass -File scripts\verify.ps1 -SkipDockerConfig
```

Run without `-SkipDockerConfig` on a machine with Docker installed to include `docker compose config`.

CI is defined in `.github/workflows/ci.yml` and runs backend tests, service reactor tests, and the frontend build.

Opt-in PostgreSQL/Testcontainers outbox test:

```powershell
cd backend
mvn.cmd test "-Dlifeline.integration.postgres=true" "-Dtest=JdbcOutboxIntegrationTest"
```

## Demo Recording Checklist

Use this checklist when adding screenshots or recording the project demo:

1. Patient signs in, checks coverage, submits a critical incident, and sees request progress.
2. Control dispatches the incident and explains the winning ambulance and hospital.
3. Driver sees only assigned operational details, advances pickup and transfer.
4. Hospital sees incoming ambulance load and updates capacity.
5. Control exhausts a hospital and reroutes an active trip.
6. Simulation compares greedy and global optimization with hospital exhaustion.
7. Control publishes outbox events and checks notification/audit state.

Suggested README media sections can be added later:

- `docs/media/patient-flow.png`
- `docs/media/driver-flow.png`
- `docs/media/hospital-flow.png`
- `docs/media/control-tower.png`
- `docs/media/simulation-run.png`
