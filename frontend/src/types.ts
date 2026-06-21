export type AmbulanceStatus = 'AVAILABLE' | 'RESERVED' | 'ON_TRIP' | 'OFFLINE';
export type AmbulanceType = 'BLS' | 'ALS' | 'ICU';
export type EmergencyCondition = 'CARDIAC' | 'TRAUMA' | 'PEDIATRIC' | 'STROKE' | 'GENERAL';
export type IncidentPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type IncidentStatus = 'NEW' | 'ASSIGNED' | 'CANCELLED' | 'COMPLETED';
export type TripStatus = 'RESERVED' | 'EN_ROUTE_PATIENT' | 'EN_ROUTE_HOSPITAL' | 'COMPLETED' | 'CANCELLED';

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
  createdAt: string;
  status: IncidentStatus;
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
}

export interface CreateIncidentPayload {
  patientName: string;
  phone: string;
  condition: EmergencyCondition;
  priority: IncidentPriority;
  latitude: number;
  longitude: number;
}
