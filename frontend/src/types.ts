export type AmbulanceStatus = 'AVAILABLE' | 'RESERVED' | 'ON_TRIP' | 'OFFLINE';
export type AmbulanceType = 'BLS' | 'ALS' | 'ICU';
export type EmergencyCondition = 'CARDIAC' | 'TRAUMA' | 'PEDIATRIC' | 'STROKE' | 'GENERAL';
export type IncidentPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type IncidentStatus = 'NEW' | 'ASSIGNED' | 'CANCELLED' | 'COMPLETED';
export type TripStatus = 'RESERVED' | 'EN_ROUTE_PATIENT' | 'EN_ROUTE_HOSPITAL' | 'COMPLETED' | 'CANCELLED';
export type NotificationRole = 'PATIENT' | 'DRIVER' | 'HOSPITAL' | 'CONTROL';
export type OptimizationStrategy = 'GREEDY_SEQUENTIAL' | 'GLOBAL_MIN_COST';
export type UserRole = 'PATIENT' | 'DRIVER' | 'HOSPITAL' | 'CONTROL';

export interface Location {
  latitude: number;
  longitude: number;
}

export interface Ambulance {
  id: string;
  callSign: string;
  type: AmbulanceType;
  status: AmbulanceStatus;
  location: Location;
  baseStation: string;
}

export interface AmbulanceLocationSnapshot {
  ambulanceId: string;
  location: Location;
  updatedAt: string;
  expiresAt: string;
  source: string;
}

export interface Hospital {
  id: string;
  name: string;
  location: Location;
  specialties: EmergencyCondition[];
  totalBeds: number;
  availableBeds: number;
  qualityScore: number;
}

export interface Incident {
  id: string;
  patientName: string;
  phone: string;
  condition: EmergencyCondition;
  priority: IncidentPriority;
  location: Location;
  addressText: string;
  landmark: string;
  locationSource: string;
  createdAt: string;
  status: IncidentStatus;
}

export type HospitalApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface HospitalApplication {
  id: string;
  hospitalName: string;
  contactName: string;
  contactPhone: string;
  addressText: string;
  location: Location;
  specialties: EmergencyCondition[];
  totalBeds: number;
  status: HospitalApplicationStatus;
  createdAt: string;
  reviewedAt: string | null;
}

export interface Trip {
  id: string;
  incidentId: string;
  ambulanceId: string;
  hospitalId: string;
  pickupEtaMinutes: number;
  hospitalEtaMinutes: number;
  totalCost: number;
  createdAt: string;
  status: TripStatus;
}

export interface CandidateScore {
  ambulanceId: string;
  hospitalId: string;
  pickupEtaMinutes: number;
  hospitalEtaMinutes: number;
  hospitalLoad: number;
  qualityPenalty: number;
  typePenalty: number;
  totalCost: number;
  explanation: string;
}

export interface DispatchResponse {
  incident: Incident;
  ambulance: Ambulance;
  hospital: Hospital;
  trip: Trip;
  winningScore: CandidateScore;
  alternatives: CandidateScore[];
}

export interface Metrics {
  openIncidents: number;
  availableAmbulances: number;
  activeTrips: number;
  hospitalsWithCapacity: number;
  averageBedAvailabilityPercent: number;
  pendingOutboxEvents: number;
  publishedOutboxEvents: number;
  failedOutboxEvents: number;
  kafkaPublishFailures: number;
  liveAmbulanceLocations: number;
  notificationBacklog: number;
  simulationRuns: number;
  latestOptimizationImprovementPercent: number;
}

export interface DispatchAuditRecord {
  id: string;
  incidentId: string;
  ambulanceId: string;
  hospitalId: string;
  pickupEtaMinutes: number;
  hospitalEtaMinutes: number;
  hospitalLoad: number;
  qualityPenalty: number;
  typePenalty: number;
  totalCost: number;
  explanation: string;
  createdAt: string;
}

export interface OutboxEvent {
  id: string;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  payload: string;
  createdAt: string;
  publishedAt: string | null;
  publishAttempts: number;
  lastPublishAttemptAt: string | null;
  lastPublishError: string | null;
  nextPublishAttemptAt: string | null;
}

export interface OutboxEventTypeSummary {
  eventType: string;
  total: number;
  pending: number;
  failed: number;
  published: number;
}

export interface OutboxSummary {
  totalEvents: number;
  pendingEvents: number;
  publishedEvents: number;
  readyEvents: number;
  failedEvents: number;
  retryScheduledEvents: number;
  oldestPendingAgeSeconds: number;
  oldestPendingAt: string | null;
  lastPublishedAt: string | null;
  eventTypes: OutboxEventTypeSummary[];
}

export interface OutboxPublishResponse {
  published: number;
  failed: number;
  pending: number;
  processedAt: string;
}

export interface Notification {
  id: string;
  role: NotificationRole;
  title: string;
  message: string;
  eventId: string;
  eventType: string;
  createdAt: string;
  acknowledgedAt: string | null;
  unread: boolean;
}

export interface CreateIncidentPayload {
  patientName: string;
  phone: string;
  condition: EmergencyCondition;
  priority: IncidentPriority;
  addressText: string;
  landmark: string;
  locationSource: string;
  latitude: number;
  longitude: number;
}

export interface CreateHospitalApplicationPayload {
  hospitalName: string;
  contactName: string;
  contactPhone: string;
  addressText: string;
  latitude: number;
  longitude: number;
  specialties: EmergencyCondition[];
  totalBeds: number;
}

export interface UpdateAmbulanceLocationPayload {
  latitude: number;
  longitude: number;
}

export interface SimulationRequestPayload {
  incidentCount: number;
  randomSeed: number;
  criticalRatio: number;
  ambulanceOutages: string[];
  exhaustedHospitals: string[];
  capacityStressPercent: number;
  strategy: OptimizationStrategy;
}

export interface SimulationAssignment {
  strategy: OptimizationStrategy;
  incidentId: string;
  condition: EmergencyCondition;
  priority: IncidentPriority;
  incidentLocation: Location;
  ambulanceId: string | null;
  hospitalId: string | null;
  pickupEtaMinutes: number;
  hospitalEtaMinutes: number;
  totalCost: number;
  matched: boolean;
  reason: string;
}

export interface SimulationStrategyResult {
  strategy: OptimizationStrategy;
  matchedCount: number;
  unmatchedCount: number;
  averagePickupEtaMinutes: number;
  averageTransferEtaMinutes: number;
  totalCost: number;
  improvementPercent: number;
  assignments: SimulationAssignment[];
}

export interface SimulationResult {
  id: string;
  request: SimulationRequestPayload;
  createdAt: string;
  strategyResults: SimulationStrategyResult[];
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthenticatedUser {
  username: string;
  displayName: string;
  role: UserRole;
  ambulanceId: string | null;
  hospitalId: string | null;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: AuthenticatedUser;
}

export interface SecurityAuditEvent {
  id: string;
  actorUserId: string;
  actorRole: string;
  action: string;
  resourceType: string;
  resourceId: string;
  outcome: string;
  reason: string;
  metadata: string;
  createdAt: string;
}

export interface PlatformServiceDescriptor {
  name: string;
  responsibility: string;
  baseUrl: string;
  healthUrl: string;
}

export interface PlatformServicesResponse {
  services: PlatformServiceDescriptor[];
}
