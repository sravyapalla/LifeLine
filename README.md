# LifeLine

LifeLine is a V1 emergency dispatch prototype that matches an incident to an ambulance and hospital in one decision.

## V1 Scope

- Java Spring Boot backend with seeded Bengaluru data
- Dispatch scoring engine for patient, ambulance, and hospital matching
- REST APIs for incidents, ambulances, hospitals, trips, and metrics
- React + TypeScript dashboard with a live operational view
- In-memory storage for fastest iteration

## Architecture

```text
frontend/
  React dashboard

backend/
  Spring Boot API
  Dispatch module
  Ambulance registry module
  Hospital capacity module
  Tracking-ready trip model
```

V2 will add PostgreSQL/PostGIS, Redis, durable reservations, and an outbox event stream.

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 20+
- npm 10+

## Run Backend

```powershell
cd backend
mvn spring-boot:run
```

Backend starts at `http://localhost:8080`.

## Run Frontend

```powershell
cd frontend
npm install
npm run dev
```

Frontend starts at `http://localhost:5173`.

## Main APIs

- `GET /api/ambulances`
- `GET /api/hospitals`
- `GET /api/incidents`
- `POST /api/incidents`
- `POST /api/dispatch`
- `GET /api/trips`
- `GET /api/metrics`
- `POST /api/demo/reset`

## Product Direction

The first version favors a coherent end-to-end dispatch loop over premature microservices. Once the workflow feels right, the backend can split into Ambulance Registry, Hospital Capacity, Dispatch, and Tracking services.

