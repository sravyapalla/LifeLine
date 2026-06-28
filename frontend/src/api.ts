import type {
  Ambulance,
  AmbulanceLocationSnapshot,
  AuthResponse,
  AuthenticatedUser,
  CreateIncidentPayload,
  CreateHospitalApplicationPayload,
  DispatchAuditRecord,
  DispatchResponse,
  Hospital,
  HospitalApplication,
  Incident,
  LoginRequest,
  Metrics,
  Notification,
  NotificationRole,
  OutboxEvent,
  OutboxPublishResponse,
  OutboxSummary,
  PlatformServicesResponse,
  SecurityAuditEvent,
  SignupRequest,
  SimulationRequestPayload,
  SimulationResult,
  Trip,
  TripStatus,
  UpdateAmbulanceLocationPayload,
  UserRole
} from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8088/api';
const TOKEN_KEY = 'lifeline.jwt';

const emptyOutboxSummary: OutboxSummary = {
  totalEvents: 0,
  pendingEvents: 0,
  publishedEvents: 0,
  readyEvents: 0,
  failedEvents: 0,
  retryScheduledEvents: 0,
  oldestPendingAgeSeconds: 0,
  oldestPendingAt: null,
  lastPublishedAt: null,
  eventTypes: []
};

export function getStoredAuthToken() {
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setAuthToken(token: string) {
  window.localStorage.setItem(TOKEN_KEY, token);
}

export function clearAuthToken() {
  window.localStorage.removeItem(TOKEN_KEY);
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getStoredAuthToken();
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers
    },
    ...options
  });

  if (!response.ok) {
    const payload = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error(payload.message ?? payload.detail ?? payload.error ?? response.statusText);
  }

  const text = await response.text();
  if (response.status === 204 || !text) {
    return undefined as T;
  }

  return JSON.parse(text) as T;
}

export async function login(payload: LoginRequest) {
  const response = await request<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  setAuthToken(response.token);
  return response;
}

export async function signup(payload: SignupRequest) {
  const response = await request<AuthResponse>('/auth/signup', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  setAuthToken(response.token);
  return response;
}

export function getCurrentUser() {
  return request<AuthenticatedUser>('/auth/me');
}

export async function getDashboardData(role: NotificationRole, userRole: UserRole) {
  const commonRequests = [
    request<Ambulance[]>('/ambulances'),
    request<AmbulanceLocationSnapshot[]>('/ambulance-locations'),
    request<Hospital[]>('/hospitals'),
    request<Incident[]>('/incidents'),
    request<Trip[]>('/trips'),
    request<Metrics>('/metrics'),
    request<Notification[]>(`/notifications?role=${role}`)
  ] as const;

  const [ambulances, liveLocations, hospitals, incidents, trips, metrics, notifications] = await Promise.all(commonRequests);

  if (userRole !== 'CONTROL') {
    return {
      ambulances,
      liveLocations,
      hospitals,
      incidents,
      trips,
      dispatchDecisions: [] as DispatchAuditRecord[],
      outboxEvents: [] as OutboxEvent[],
      outboxSummary: emptyOutboxSummary,
      metrics,
      notifications,
      simulations: [] as SimulationResult[]
    };
  }

  const [dispatchDecisions, outboxEvents, outboxSummary, simulations] = await Promise.all([
    request<DispatchAuditRecord[]>('/dispatch-decisions'),
    request<OutboxEvent[]>('/outbox-events'),
    request<OutboxSummary>('/outbox-events/summary'),
    request<SimulationResult[]>('/simulations')
  ]);

  return { ambulances, liveLocations, hospitals, incidents, trips, dispatchDecisions, outboxEvents, outboxSummary, metrics, notifications, simulations };
}

export function getPendingUserApprovals() {
  return request<AuthenticatedUser[]>('/users/pending-approvals');
}

export function approveUser(username: string) {
  return request<AuthenticatedUser>(`/users/${encodeURIComponent(username)}/approve`, {
    method: 'POST'
  });
}

export function getAuditEvents(limit = 100) {
  return request<SecurityAuditEvent[]>(`/audit-events?limit=${limit}`);
}

export function getPlatformServices() {
  return request<PlatformServicesResponse>('/platform/services');
}

export function createIncident(payload: CreateIncidentPayload) {
  return request<Incident>('/incidents', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function createHospitalApplication(payload: CreateHospitalApplicationPayload) {
  return request<HospitalApplication>('/hospital-applications', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function getHospitalApplications() {
  return request<HospitalApplication[]>('/hospital-applications');
}

export function approveHospitalApplication(applicationId: string) {
  return request<HospitalApplication>(`/hospital-applications/${applicationId}/approve`, {
    method: 'POST'
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

export function updateAmbulanceLocation(ambulanceId: string, payload: UpdateAmbulanceLocationPayload) {
  return request<AmbulanceLocationSnapshot>(`/ambulances/${ambulanceId}/location`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function acknowledgeNotification(notificationId: string) {
  return request<Notification>(`/notifications/${notificationId}/ack`, {
    method: 'POST'
  });
}

export function runSimulation(payload: SimulationRequestPayload) {
  return request<SimulationResult>('/simulations', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function getSimulation(simulationId: string) {
  return request<SimulationResult>(`/simulations/${simulationId}`);
}
