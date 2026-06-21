import type {
  Ambulance,
  CreateIncidentPayload,
  DispatchAuditRecord,
  DispatchResponse,
  Hospital,
  Incident,
  Metrics,
  OutboxEvent,
  OutboxPublishResponse,
  OutboxSummary,
  Trip,
  TripStatus
} from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers
    },
    ...options
  });

  if (!response.ok) {
    const payload = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error(payload.message ?? response.statusText);
  }

  const text = await response.text();
  if (response.status === 204 || !text) {
    return undefined as T;
  }

  return JSON.parse(text) as T;
}

export async function getDashboardData() {
  const [ambulances, hospitals, incidents, trips, dispatchDecisions, outboxEvents, outboxSummary, metrics] = await Promise.all([
    request<Ambulance[]>('/ambulances'),
    request<Hospital[]>('/hospitals'),
    request<Incident[]>('/incidents'),
    request<Trip[]>('/trips'),
    request<DispatchAuditRecord[]>('/dispatch-decisions'),
    request<OutboxEvent[]>('/outbox-events'),
    request<OutboxSummary>('/outbox-events/summary'),
    request<Metrics>('/metrics')
  ]);

  return { ambulances, hospitals, incidents, trips, dispatchDecisions, outboxEvents, outboxSummary, metrics };
}

export function createIncident(payload: CreateIncidentPayload) {
  return request<Incident>('/incidents', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function dispatchIncident(incidentId: string) {
  return request<DispatchResponse>('/dispatch', {
    method: 'POST',
    body: JSON.stringify({ incidentId })
  });
}

export function resetDemo() {
  return request<void>('/demo/reset', {
    method: 'POST'
  });
}

export function updateTripStatus(tripId: string, status: TripStatus) {
  return request<Trip>(`/trips/${tripId}/status`, {
    method: 'POST',
    body: JSON.stringify({ status })
  });
}

export function updateHospitalCapacity(hospitalId: string, availableBeds: number) {
  return request<Hospital>(`/hospitals/${hospitalId}/capacity`, {
    method: 'POST',
    body: JSON.stringify({ availableBeds })
  });
}

export function rerouteTrip(tripId: string) {
  return request<DispatchResponse>(`/trips/${tripId}/reroute`, {
    method: 'POST'
  });
}

export function publishOutboxEvents() {
  return request<OutboxPublishResponse>('/outbox-events/publish', {
    method: 'POST'
  });
}
