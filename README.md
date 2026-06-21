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

## System Architecture

LifeLine uses a distributed service architecture with a practical migration path. The existing backend remains the operational source while V7 introduces deployable service boundaries and a gateway so the system can be split without rewriting product behavior.

<img width="1536" height="1024" alt="ChatGPT Image Jun 21, 2026, 10_37_56 PM" src="https://github.com/user-attachments/assets/79f5d249-b017-4d75-ba7b-5dfacfe12813" />


### Service Boundaries

| Service | Owns | Why It Is Separate |
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

V7 keeps a strangler-style transition: the gateway can route to the current operations backend while new service shells are deployed and verified. The important engineering decision is that API contracts, data ownership, and runtime deployment are explicit now, so future extraction is a move with contract tests rather than a rewrite.

## Data Architecture

| Data | System of Record | Notes |
| --- | --- | --- |
| Incidents | PostgreSQL/PostGIS | Durable emergency records with requester ownership |
| Ambulance profile | PostgreSQL/PostGIS | Fleet identity, capability, base location, operational status |
| Ambulance live location | Redis with TTL | Fast, ephemeral projection; dispatch falls back to persisted location |
| Hospital profile and capacity | PostgreSQL/PostGIS | Bed availability is authoritative and auditable |
| Trips | PostgreSQL/PostGIS | Assignment and care journey lifecycle |
| Dispatch decisions | PostgreSQL/PostGIS | Candidate scores, winning reason, alternatives |
| Outbox events | PostgreSQL/PostGIS, then Kafka | Transactional outbox protects state change and event publishing |
| Notifications | PostgreSQL/PostGIS or memory profile | Role-targeted read model generated from workflow events |
| Security audit | PostgreSQL/PostGIS or memory profile | Login, denied access, dispatch, reroute, capacity, publish, simulation, ack |
| Simulation runs | PostgreSQL/PostGIS or memory profile | Deterministic scenario history and strategy comparison |

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

LifeLine uses local JWT authentication for a self-contained demo and keeps external OAuth plug-in-ready for a later production integration.

### Demo Users

Set `LIFELINE_DEMO_PASSWORD` locally and use it with these demo accounts:

| Username | Role | Scope |
| --- | --- | --- |
| `patient.demo` | `PATIENT` | Own seeded and newly requested incidents |
| `driver.demo` | `DRIVER` | Ambulance `AMB-101` and assigned trips |
| `hospital.demo` | `HOSPITAL` | Hospital `HOS-201` and incoming trips |
| `control.demo` | `CONTROL` | Full operational access |

JWT claims include `sub`, `role`, optional `ambulanceId`, optional `hospitalId`, `iat`, and `exp`.

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
scripts/verify-v7.ps1    Local verification entry point
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
- `GET /api/auth/me`

### Operations

- `GET /api/ambulances`
- `POST /api/ambulances/{id}/location`
- `GET /api/ambulance-locations`
- `GET /api/hospitals`
- `POST /api/hospitals/{hospitalId}/capacity`
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

V7 targets a production-ready local and container deployment shape:

- Docker Compose for full local orchestration.
- A gateway service as the only browser-facing API.
- Separate deployable service boundaries for incident, resource, dispatch, notification, audit, and simulation.
- Kubernetes manifests for a cloud-ready baseline.
- Environment-driven secrets.
- Health endpoints for every service.
- PostgreSQL, Redis, and Kafka as explicit infrastructure dependencies.

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
| D06 | Gateway first for microservices | Browsers should depend on one public API boundary while services evolve internally |
| D07 | Strangler migration over big-bang rewrite | The current backend works; service extraction should preserve behavior while boundaries become deployable |
| D08 | Backend-enforced RBAC | The API is the trust boundary; frontend route hiding is only UX |
| D09 | PII masking at response mapping | Domain logic keeps needed data while each role receives the least data required |
| D10 | Audit allowed and denied actions | Trust requires visibility into both successful sensitive actions and blocked attempts |
| D11 | Routing provider abstraction | Dispatch should depend on route estimates, not a specific vendor or engine |
| D12 | Explainable dispatch before opaque ML | Emergency workflows need deterministic, auditable decisions before assistive prediction |
| D13 | Global optimization as simulator comparison | Batch optimization teaches tradeoffs without replacing real-time dispatch prematurely |
| D14 | Role-specific UI surfaces | A patient, driver, hospital operator, and control tower need different mental models and workflows |
| D15 | Environment-driven secrets | Demo convenience must not require committed credentials |
| D16 | Health endpoints everywhere | Distributed systems need fast runtime inspection and deployability checks |

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
powershell.exe -ExecutionPolicy Bypass -File scripts\verify-v7.ps1 -SkipDockerConfig
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
