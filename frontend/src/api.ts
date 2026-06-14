import type {
  Ambulance,
  CreateIncidentPayload,
  DispatchResponse,
  Hospital,
  Incident,
  Metrics,
  Trip
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
  const [ambulances, hospitals, incidents, trips, metrics] = await Promise.all([
    request<Ambulance[]>('/ambulances'),
    request<Hospital[]>('/hospitals'),
    request<Incident[]>('/incidents'),
    request<Trip[]>('/trips'),
    request<Metrics>('/metrics')
  ]);

  return { ambulances, hospitals, incidents, trips, metrics };
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

