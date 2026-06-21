import { useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  Activity,
  Ambulance as AmbulanceIcon,
  Bed,
  BellPlus,
  CheckCircle2,
  ClipboardList,
  HeartPulse,
  Hospital as HospitalIcon,
  MapPin,
  Navigation,
  RadioTower,
  RefreshCw,
  Route,
  RotateCcw,
  Siren,
  UserRound
} from 'lucide-react';
import { CircleMarker, MapContainer, Polyline, Popup, TileLayer } from 'react-leaflet';
import {
  createIncident,
  dispatchIncident,
  getDashboardData,
  rerouteTrip,
  resetDemo,
  updateHospitalCapacity,
  updateTripStatus
} from './api';
import type {
  Ambulance,
  CreateIncidentPayload,
  DispatchAuditRecord,
  DispatchResponse,
  EmergencyCondition,
  Hospital,
  Incident,
  IncidentPriority,
  Metrics,
  OutboxEvent,
  Trip,
  TripStatus
} from './types';

type Role = 'patient' | 'driver' | 'hospital' | 'control';

interface DashboardState {
  ambulances: Ambulance[];
  hospitals: Hospital[];
  incidents: Incident[];
  trips: Trip[];
  dispatchDecisions: DispatchAuditRecord[];
  outboxEvents: OutboxEvent[];
  metrics: Metrics;
}

const initialIncident: CreateIncidentPayload = {
  patientName: 'New Patient',
  phone: '+91-90000-12345',
  condition: 'CARDIAC',
  priority: 'CRITICAL',
  latitude: 12.9458,
  longitude: 77.6309
};

const conditions: EmergencyCondition[] = ['CARDIAC', 'TRAUMA', 'PEDIATRIC', 'STROKE', 'GENERAL'];
const priorities: IncidentPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

const roles: { id: Role; label: string; icon: ReactNode }[] = [
  { id: 'patient', label: 'Patient', icon: <UserRound size={17} /> },
  { id: 'driver', label: 'Driver', icon: <AmbulanceIcon size={17} /> },
  { id: 'hospital', label: 'Hospital', icon: <HospitalIcon size={17} /> },
  { id: 'control', label: 'Control', icon: <RadioTower size={17} /> }
];

const activeTripStatuses: TripStatus[] = ['RESERVED', 'EN_ROUTE_PATIENT', 'EN_ROUTE_HOSPITAL'];

export default function App() {
  const [role, setRole] = useState<Role>(() => roleFromPath(window.location.pathname));
  const [data, setData] = useState<DashboardState | null>(null);
  const [selectedIncidentId, setSelectedIncidentId] = useState('');
  const [selectedTripId, setSelectedTripId] = useState('');
  const [form, setForm] = useState<CreateIncidentPayload>(initialIncident);
  const [capacityDrafts, setCapacityDrafts] = useState<Record<string, number>>({});
  const [lastDecision, setLastDecision] = useState<DispatchResponse | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function load() {
    setError('');
    const nextData = await getDashboardData();
    setData(nextData);
    setCapacityDrafts(Object.fromEntries(nextData.hospitals.map((hospital) => [hospital.id, hospital.availableBeds])));
    setSelectedIncidentId((current) => {
      if (nextData.incidents.some((incident) => incident.id === current)) return current;
      const firstNewIncident = nextData.incidents.find((incident) => incident.status === 'NEW');
      return firstNewIncident?.id ?? nextData.incidents[0]?.id ?? '';
    });
    setSelectedTripId((current) => {
      if (nextData.trips.some((trip) => trip.id === current)) return current;
      const firstActiveTrip = nextData.trips.find((trip) => activeTripStatuses.includes(trip.status));
      return firstActiveTrip?.id ?? nextData.trips[0]?.id ?? '';
    });
  }

  useEffect(() => {
    load().catch((loadError: Error) => setError(loadError.message));
  }, []);

  useEffect(() => {
    function syncPath() {
      setRole(roleFromPath(window.location.pathname));
    }

    window.addEventListener('popstate', syncPath);
    return () => window.removeEventListener('popstate', syncPath);
  }, []);

  const selectedIncident = useMemo(
    () => data?.incidents.find((incident) => incident.id === selectedIncidentId) ?? null,
    [data?.incidents, selectedIncidentId]
  );

  const selectedTrip = useMemo(
    () => data?.trips.find((trip) => trip.id === selectedTripId) ?? null,
    [data?.trips, selectedTripId]
  );

  function navigateRole(nextRole: Role) {
    setRole(nextRole);
    window.history.pushState(null, '', rolePath(nextRole));
  }

  async function handleCreateIncident() {
    setBusy(true);
    setError('');
    try {
      const incident = await createIncident(form);
      await load();
      setSelectedIncidentId(incident.id);
      navigateRole('control');
    } catch (createError) {
      setError((createError as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleDispatch(incidentId = selectedIncidentId) {
    if (!incidentId) return;
    setBusy(true);
    setError('');
    try {
      const decision = await dispatchIncident(incidentId);
      setLastDecision(decision);
      await load();
      setSelectedIncidentId(decision.incident.id);
      setSelectedTripId(decision.trip.id);
    } catch (dispatchError) {
      setError((dispatchError as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleTripStatus(tripId: string, status: TripStatus) {
    setBusy(true);
    setError('');
    try {
      const trip = await updateTripStatus(tripId, status);
      await load();
      setSelectedTripId(trip.id);
    } catch (statusError) {
      setError((statusError as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleHospitalCapacity(hospitalId: string, availableBeds: number) {
    setBusy(true);
    setError('');
    try {
      const hospital = await updateHospitalCapacity(hospitalId, availableBeds);
      await load();
      setCapacityDrafts((drafts) => ({ ...drafts, [hospital.id]: hospital.availableBeds }));
    } catch (capacityError) {
      setError((capacityError as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleReroute(tripId: string) {
    setBusy(true);
    setError('');
    try {
      const decision = await rerouteTrip(tripId);
      setLastDecision(decision);
      await load();
      setSelectedIncidentId(decision.incident.id);
      setSelectedTripId(decision.trip.id);
      navigateRole('control');
    } catch (rerouteError) {
      setError((rerouteError as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleReset() {
    setBusy(true);
    setError('');
    try {
      await resetDemo();
      setLastDecision(null);
      setSelectedIncidentId('');
      setSelectedTripId('');
      await load();
    } catch (resetError) {
      setError((resetError as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const metrics = data?.metrics;
  const activeTrips = data?.trips.filter((trip) => activeTripStatuses.includes(trip.status)) ?? [];

  return (
    <main className="shell">
      <header className="topbar">
        <div className="brand-block">
          <p className="eyebrow">LifeLine V3</p>
          <h1>Multi-Actor Emergency Workflow</h1>
        </div>

        <nav className="role-tabs" aria-label="Role navigation">
          {roles.map((item) => (
            <button
              key={item.id}
              className={`role-tab ${role === item.id ? 'active' : ''}`}
              type="button"
              onClick={() => navigateRole(item.id)}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          ))}
        </nav>

        <div className="actions">
          <button className="icon-button" type="button" onClick={() => load().catch((loadError: Error) => setError(loadError.message))} aria-label="Refresh dashboard" title="Refresh dashboard">
            <RefreshCw size={18} />
          </button>
          <button className="ghost-button" type="button" onClick={handleReset} disabled={busy}>
            <RotateCcw size={16} />
            Reset
          </button>
        </div>
      </header>

      {error && <div className="alert">{error}</div>}

      <section className="metrics-grid">
        <MetricCard icon={<Siren size={18} />} label="Open" value={metrics?.openIncidents ?? 0} tone="red" />
        <MetricCard icon={<AmbulanceIcon size={18} />} label="Available" value={metrics?.availableAmbulances ?? 0} tone="green" />
        <MetricCard icon={<Route size={18} />} label="Active Trips" value={activeTrips.length} tone="blue" />
        <MetricCard icon={<Bed size={18} />} label="Bed Avg" value={`${metrics?.averageBedAvailabilityPercent ?? 0}%`} tone="amber" />
      </section>

      {role === 'patient' && (
        <PatientView
          data={data}
          form={form}
          setForm={setForm}
          selectedIncidentId={selectedIncidentId}
          setSelectedIncidentId={setSelectedIncidentId}
          onCreateIncident={handleCreateIncident}
          busy={busy}
        />
      )}

      {role === 'driver' && (
        <DriverView
          data={data}
          selectedTripId={selectedTripId}
          setSelectedTripId={setSelectedTripId}
          onTripStatus={handleTripStatus}
          busy={busy}
        />
      )}

      {role === 'hospital' && (
        <HospitalView
          data={data}
          capacityDrafts={capacityDrafts}
          setCapacityDrafts={setCapacityDrafts}
          onHospitalCapacity={handleHospitalCapacity}
          onReroute={handleReroute}
          busy={busy}
        />
      )}

      {role === 'control' && (
        <ControlView
          data={data}
          selectedIncident={selectedIncident}
          selectedTrip={selectedTrip}
          selectedIncidentId={selectedIncidentId}
          selectedTripId={selectedTripId}
          setSelectedIncidentId={setSelectedIncidentId}
          setSelectedTripId={setSelectedTripId}
          onDispatch={handleDispatch}
          onReroute={handleReroute}
          lastDecision={lastDecision}
          busy={busy}
        />
      )}
    </main>
  );
}

function PatientView({
  data,
  form,
  setForm,
  selectedIncidentId,
  setSelectedIncidentId,
  onCreateIncident,
  busy
}: {
  data: DashboardState | null;
  form: CreateIncidentPayload;
  setForm: (form: CreateIncidentPayload) => void;
  selectedIncidentId: string;
  setSelectedIncidentId: (id: string) => void;
  onCreateIncident: () => void;
  busy: boolean;
}) {
  const selectedIncident = data?.incidents.find((incident) => incident.id === selectedIncidentId) ?? null;
  const selectedTrip = selectedIncident ? data?.trips.find((trip) => trip.incidentId === selectedIncident.id) ?? null : null;
  const assignedAmbulance = selectedTrip ? data?.ambulances.find((ambulance) => ambulance.id === selectedTrip.ambulanceId) : null;
  const receivingHospital = selectedTrip ? data?.hospitals.find((hospital) => hospital.id === selectedTrip.hospitalId) : null;

  return (
    <section className="role-layout patient-layout">
      <aside className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Patient</p>
            <h2>Emergency Request</h2>
          </div>
          <BellPlus size={18} />
        </div>

        <IncidentForm form={form} setForm={setForm} onSubmit={onCreateIncident} busy={busy} submitLabel="Request Ambulance" />
      </aside>

      <section className="map-stage">
        <MapPanel
          ambulances={data?.ambulances ?? []}
          hospitals={data?.hospitals ?? []}
          incidents={data?.incidents ?? []}
          trips={data?.trips ?? []}
          selectedIncidentId={selectedIncidentId}
          selectedTripId={selectedTrip?.id ?? ''}
        />
      </section>

      <aside className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Status</p>
            <h2>Care Journey</h2>
          </div>
          <HeartPulse size={18} />
        </div>

        {selectedIncident ? (
          <div className="journey-panel">
            <span className={`status-badge ${selectedIncident.status.toLowerCase()}`}>{formatStatus(selectedIncident.status)}</span>
            <h3>{selectedIncident.patientName}</h3>
            <p>{selectedIncident.condition} - {selectedIncident.priority}</p>
            <JourneySteps incident={selectedIncident} trip={selectedTrip ?? null} />
            {assignedAmbulance && <InfoLine label="Ambulance" value={`${assignedAmbulance.callSign} - ${assignedAmbulance.type}`} />}
            {receivingHospital && <InfoLine label="Hospital" value={receivingHospital.name} />}
          </div>
        ) : (
          <div className="empty-state">No active request</div>
        )}

        <div className="compact-list">
          <h3>Recent Requests</h3>
          {data?.incidents.map((incident) => (
            <button
              key={incident.id}
              className={`compact-row ${incident.id === selectedIncidentId ? 'selected' : ''}`}
              type="button"
              onClick={() => setSelectedIncidentId(incident.id)}
            >
              <span className={`priority-dot ${incident.priority.toLowerCase()}`} />
              <span>
                <strong>{incident.patientName}</strong>
                <small>{formatStatus(incident.status)}</small>
              </span>
            </button>
          ))}
        </div>
      </aside>
    </section>
  );
}

function DriverView({
  data,
  selectedTripId,
  setSelectedTripId,
  onTripStatus,
  busy
}: {
  data: DashboardState | null;
  selectedTripId: string;
  setSelectedTripId: (id: string) => void;
  onTripStatus: (tripId: string, status: TripStatus) => void;
  busy: boolean;
}) {
  const selectedTrip = data?.trips.find((trip) => trip.id === selectedTripId) ?? null;

  return (
    <section className="role-layout driver-layout">
      <aside className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Fleet</p>
            <h2>Ambulances</h2>
          </div>
          <AmbulanceIcon size={18} />
        </div>

        <div className="resource-list">
          {data?.ambulances.map((ambulance) => (
            <div className="resource-row" key={ambulance.id}>
              <span className={`resource-dot ${ambulance.status.toLowerCase()}`} />
              <span>{ambulance.callSign}</span>
              <em>{formatStatus(ambulance.status)}</em>
            </div>
          ))}
        </div>
      </aside>

      <section className="panel wide-panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Driver</p>
            <h2>Assigned Trips</h2>
          </div>
          <Navigation size={18} />
        </div>

        <div className="trip-grid">
          {data?.trips.map((trip) => (
            <TripCard
              key={trip.id}
              trip={trip}
              data={data}
              selected={trip.id === selectedTripId}
              onSelect={() => setSelectedTripId(trip.id)}
              actions={<DriverActions trip={trip} onTripStatus={onTripStatus} busy={busy} />}
            />
          ))}
        </div>
      </section>

      <aside className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Route</p>
            <h2>Trip Map</h2>
          </div>
          <MapPin size={18} />
        </div>
        <MapPanel
          ambulances={data?.ambulances ?? []}
          hospitals={data?.hospitals ?? []}
          incidents={data?.incidents ?? []}
          trips={data?.trips ?? []}
          selectedIncidentId={selectedTrip?.incidentId ?? ''}
          selectedTripId={selectedTrip?.id ?? ''}
          compact
        />
      </aside>
    </section>
  );
}

function HospitalView({
  data,
  capacityDrafts,
  setCapacityDrafts,
  onHospitalCapacity,
  onReroute,
  busy
}: {
  data: DashboardState | null;
  capacityDrafts: Record<string, number>;
  setCapacityDrafts: (drafts: Record<string, number>) => void;
  onHospitalCapacity: (hospitalId: string, availableBeds: number) => void;
  onReroute: (tripId: string) => void;
  busy: boolean;
}) {
  return (
    <section className="hospital-grid">
      {data?.hospitals.map((hospital) => {
        const incomingTrips = data.trips.filter((trip) => trip.hospitalId === hospital.id && activeTripStatuses.includes(trip.status));
        const draft = capacityDrafts[hospital.id] ?? hospital.availableBeds;

        return (
          <article className="panel hospital-card" key={hospital.id}>
            <div className="panel-heading">
              <div>
                <p className="eyebrow">{hospital.id}</p>
                <h2>{hospital.name}</h2>
              </div>
              <span className={`capacity-pill ${hospital.availableBeds === 0 ? 'exhausted' : ''}`}>{hospital.availableBeds}/{hospital.totalBeds}</span>
            </div>

            <div className="specialty-row">
              {hospital.specialties.map((specialty) => <span key={specialty}>{specialty}</span>)}
            </div>

            <div className="capacity-control">
              <label>
                Beds
                <input
                  type="number"
                  min={0}
                  max={hospital.totalBeds}
                  value={draft}
                  onChange={(event) => setCapacityDrafts({ ...capacityDrafts, [hospital.id]: Number(event.target.value) })}
                />
              </label>
              <button className="primary-button" type="button" onClick={() => onHospitalCapacity(hospital.id, draft)} disabled={busy}>
                Update
              </button>
              <button className="ghost-button full-width" type="button" onClick={() => onHospitalCapacity(hospital.id, 0)} disabled={busy}>
                Mark Exhausted
              </button>
            </div>

            <div className="compact-list">
              <h3>Incoming</h3>
              {incomingTrips.length === 0 && <div className="empty-state compact">No incoming trips</div>}
              {incomingTrips.map((trip) => (
                <HospitalTripRow key={trip.id} trip={trip} data={data} onReroute={onReroute} busy={busy} />
              ))}
            </div>
          </article>
        );
      })}
    </section>
  );
}

function ControlView({
  data,
  selectedIncident,
  selectedTrip,
  selectedIncidentId,
  selectedTripId,
  setSelectedIncidentId,
  setSelectedTripId,
  onDispatch,
  onReroute,
  lastDecision,
  busy
}: {
  data: DashboardState | null;
  selectedIncident: Incident | null;
  selectedTrip: Trip | null;
  selectedIncidentId: string;
  selectedTripId: string;
  setSelectedIncidentId: (id: string) => void;
  setSelectedTripId: (id: string) => void;
  onDispatch: (incidentId?: string) => void;
  onReroute: (tripId: string) => void;
  lastDecision: DispatchResponse | null;
  busy: boolean;
}) {
  return (
    <section className="role-layout control-layout">
      <aside className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Queue</p>
            <h2>Incidents</h2>
          </div>
          <span className="count">{data?.incidents.length ?? 0}</span>
        </div>

        <div className="incident-list">
          {data?.incidents.map((incident) => (
            <button
              key={incident.id}
              className={`incident-row ${incident.id === selectedIncidentId ? 'selected' : ''}`}
              type="button"
              onClick={() => setSelectedIncidentId(incident.id)}
            >
              <span className={`priority-dot ${incident.priority.toLowerCase()}`} />
              <span>
                <strong>{incident.patientName}</strong>
                <small>{incident.condition} - {incident.priority}</small>
              </span>
              <em>{formatStatus(incident.status)}</em>
            </button>
          ))}
        </div>

        <div className="compact-list bordered">
          <h3>Trips</h3>
          {data?.trips.map((trip) => (
            <button
              key={trip.id}
              className={`compact-row ${trip.id === selectedTripId ? 'selected' : ''}`}
              type="button"
              onClick={() => setSelectedTripId(trip.id)}
            >
              <span className="route-dot" />
              <span>
                <strong>{trip.id}</strong>
                <small>{formatStatus(trip.status)}</small>
              </span>
            </button>
          ))}
        </div>
      </aside>

      <section className="map-stage">
        <MapPanel
          ambulances={data?.ambulances ?? []}
          hospitals={data?.hospitals ?? []}
          incidents={data?.incidents ?? []}
          trips={data?.trips ?? []}
          selectedIncidentId={selectedIncidentId}
          selectedTripId={selectedTripId}
        />
      </section>

      <aside className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Decision</p>
            <h2>Control Tower</h2>
          </div>
          <Activity size={18} />
        </div>

        {selectedIncident ? (
          <div className="selected-call">
            <span className={`status-badge ${selectedIncident.status.toLowerCase()}`}>{formatStatus(selectedIncident.status)}</span>
            <h3>{selectedIncident.patientName}</h3>
            <p>{selectedIncident.condition} - {selectedIncident.priority}</p>
            <button className="primary-button dispatch-button" type="button" onClick={() => onDispatch(selectedIncident.id)} disabled={busy || selectedIncident.status !== 'NEW'}>
              Run Dispatch
            </button>
          </div>
        ) : (
          <div className="empty-state">No incident selected</div>
        )}

        {selectedTrip && (
          <div className="selected-call secondary">
            <h3>{selectedTrip.id}</h3>
            <p>{formatStatus(selectedTrip.status)}</p>
            <button className="ghost-button full-width" type="button" onClick={() => onReroute(selectedTrip.id)} disabled={busy || !activeTripStatuses.includes(selectedTrip.status)}>
              Reroute
            </button>
          </div>
        )}

        {lastDecision && <DecisionResult decision={lastDecision} />}

        <Timeline events={data?.outboxEvents ?? []} decisions={data?.dispatchDecisions ?? []} />
      </aside>
    </section>
  );
}

function IncidentForm({
  form,
  setForm,
  onSubmit,
  busy,
  submitLabel
}: {
  form: CreateIncidentPayload;
  setForm: (form: CreateIncidentPayload) => void;
  onSubmit: () => void;
  busy: boolean;
  submitLabel: string;
}) {
  return (
    <div className="create-form open">
      <label>
        Patient
        <input value={form.patientName} onChange={(event) => setForm({ ...form, patientName: event.target.value })} />
      </label>
      <label>
        Phone
        <input value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} />
      </label>
      <label>
        Condition
        <select value={form.condition} onChange={(event) => setForm({ ...form, condition: event.target.value as EmergencyCondition })}>
          {conditions.map((condition) => <option key={condition} value={condition}>{condition}</option>)}
        </select>
      </label>
      <label>
        Priority
        <select value={form.priority} onChange={(event) => setForm({ ...form, priority: event.target.value as IncidentPriority })}>
          {priorities.map((priority) => <option key={priority} value={priority}>{priority}</option>)}
        </select>
      </label>
      <div className="coordinate-grid">
        <label>
          Lat
          <input type="number" value={form.latitude} onChange={(event) => setForm({ ...form, latitude: Number(event.target.value) })} />
        </label>
        <label>
          Lng
          <input type="number" value={form.longitude} onChange={(event) => setForm({ ...form, longitude: Number(event.target.value) })} />
        </label>
      </div>
      <button className="primary-button" type="button" onClick={onSubmit} disabled={busy}>
        {submitLabel}
      </button>
    </div>
  );
}

function DriverActions({ trip, onTripStatus, busy }: { trip: Trip; onTripStatus: (tripId: string, status: TripStatus) => void; busy: boolean }) {
  if (trip.status === 'RESERVED') {
    return <button className="primary-button" type="button" onClick={() => onTripStatus(trip.id, 'EN_ROUTE_PATIENT')} disabled={busy}>Start Pickup</button>;
  }
  if (trip.status === 'EN_ROUTE_PATIENT') {
    return <button className="primary-button" type="button" onClick={() => onTripStatus(trip.id, 'EN_ROUTE_HOSPITAL')} disabled={busy}>Patient Picked Up</button>;
  }
  if (trip.status === 'EN_ROUTE_HOSPITAL') {
    return <button className="primary-button" type="button" onClick={() => onTripStatus(trip.id, 'COMPLETED')} disabled={busy}>Arrived</button>;
  }
  return <button className="ghost-button full-width" type="button" disabled>{formatStatus(trip.status)}</button>;
}

function HospitalTripRow({ trip, data, onReroute, busy }: { trip: Trip; data: DashboardState; onReroute: (tripId: string) => void; busy: boolean }) {
  const incident = data.incidents.find((candidate) => candidate.id === trip.incidentId);
  const ambulance = data.ambulances.find((candidate) => candidate.id === trip.ambulanceId);

  return (
    <div className="incoming-row">
      <div>
        <strong>{incident?.patientName ?? trip.incidentId}</strong>
        <small>{ambulance?.callSign ?? trip.ambulanceId} - {formatStatus(trip.status)}</small>
      </div>
      <button className="ghost-button" type="button" onClick={() => onReroute(trip.id)} disabled={busy}>
        Reroute
      </button>
    </div>
  );
}

function TripCard({
  trip,
  data,
  selected,
  onSelect,
  actions
}: {
  trip: Trip;
  data: DashboardState;
  selected: boolean;
  onSelect: () => void;
  actions: ReactNode;
}) {
  const incident = data.incidents.find((candidate) => candidate.id === trip.incidentId);
  const ambulance = data.ambulances.find((candidate) => candidate.id === trip.ambulanceId);
  const hospital = data.hospitals.find((candidate) => candidate.id === trip.hospitalId);

  return (
    <article className={`trip-card ${selected ? 'selected' : ''}`}>
      <button className="trip-select" type="button" onClick={onSelect}>
        <span className={`status-badge ${trip.status.toLowerCase()}`}>{formatStatus(trip.status)}</span>
        <strong>{incident?.patientName ?? trip.incidentId}</strong>
        <small>{ambulance?.callSign ?? trip.ambulanceId} to {hospital?.name ?? trip.hospitalId}</small>
      </button>
      <div className="route-summary">
        <span>{trip.pickupEtaMinutes} min pickup</span>
        <span>{trip.hospitalEtaMinutes} min transfer</span>
      </div>
      {actions}
    </article>
  );
}

function DecisionResult({ decision }: { decision: DispatchResponse }) {
  return (
    <div className="decision-result">
      <h3>{decision.ambulance.callSign}</h3>
      <p>{decision.ambulance.type} from {decision.ambulance.baseStation}</p>
      <div className="route-summary">
        <span>{decision.trip.pickupEtaMinutes} min pickup</span>
        <span>{decision.trip.hospitalEtaMinutes} min transfer</span>
      </div>
      <h3>{decision.hospital.name}</h3>
      <p>{decision.hospital.availableBeds} beds after reservation</p>
      <div className="score-box">
        <strong>Score {decision.winningScore.totalCost}</strong>
        <small>{decision.winningScore.explanation}</small>
      </div>
      <div className="candidate-list">
        <h4>Top Candidates</h4>
        {decision.alternatives.slice(0, 5).map((candidate, index) => (
          <div className="candidate-row" key={`${candidate.ambulanceId}-${candidate.hospitalId}-${index}`}>
            <span>{index + 1}</span>
            <div>
              <strong>{candidate.ambulanceId} to {candidate.hospitalId}</strong>
              <small>{candidate.explanation}</small>
            </div>
            <em>{candidate.totalCost}</em>
          </div>
        ))}
      </div>
    </div>
  );
}

function Timeline({ events, decisions }: { events: OutboxEvent[]; decisions: DispatchAuditRecord[] }) {
  const rows = [
    ...events.map((event) => ({
      id: event.id,
      type: event.eventType,
      subject: `${event.aggregateType} ${event.aggregateId}`,
      at: event.createdAt
    })),
    ...decisions.map((decision) => ({
      id: decision.id,
      type: 'dispatch.decision',
      subject: `${decision.ambulanceId} to ${decision.hospitalId}`,
      at: decision.createdAt
    }))
  ].sort((left, right) => new Date(right.at).getTime() - new Date(left.at).getTime()).slice(0, 8);

  return (
    <div className="timeline">
      <h3>Timeline</h3>
      {rows.length === 0 && <div className="empty-state compact">No events yet</div>}
      {rows.map((row) => (
        <div className="timeline-row" key={row.id}>
          <span />
          <div>
            <strong>{row.type}</strong>
            <small>{row.subject}</small>
          </div>
        </div>
      ))}
    </div>
  );
}

function JourneySteps({ incident, trip }: { incident: Incident; trip: Trip | null }) {
  const steps = [
    { label: 'Requested', done: true },
    { label: 'Matched', done: incident.status === 'ASSIGNED' || incident.status === 'COMPLETED' },
    { label: 'Pickup', done: trip?.status === 'EN_ROUTE_PATIENT' || trip?.status === 'EN_ROUTE_HOSPITAL' || trip?.status === 'COMPLETED' },
    { label: 'Hospital', done: trip?.status === 'EN_ROUTE_HOSPITAL' || trip?.status === 'COMPLETED' },
    { label: 'Complete', done: incident.status === 'COMPLETED' }
  ];

  return (
    <div className="journey-steps">
      {steps.map((step) => (
        <div className={`journey-step ${step.done ? 'done' : ''}`} key={step.label}>
          <CheckCircle2 size={16} />
          <span>{step.label}</span>
        </div>
      ))}
    </div>
  );
}

function InfoLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="info-line">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function MetricCard({ icon, label, value, tone }: { icon: ReactNode; label: string; value: number | string; tone: string }) {
  return (
    <article className={`metric-card ${tone}`}>
      <span>{icon}</span>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
      </div>
    </article>
  );
}

function MapPanel({
  ambulances,
  hospitals,
  incidents,
  trips,
  selectedIncidentId,
  selectedTripId,
  compact = false
}: {
  ambulances: Ambulance[];
  hospitals: Hospital[];
  incidents: Incident[];
  trips: Trip[];
  selectedIncidentId: string;
  selectedTripId: string;
  compact?: boolean;
}) {
  const selectedTrip = trips.find((trip) => trip.id === selectedTripId);
  const tripIncident = selectedTrip ? incidents.find((incident) => incident.id === selectedTrip.incidentId) : null;
  const tripAmbulance = selectedTrip ? ambulances.find((ambulance) => ambulance.id === selectedTrip.ambulanceId) : null;
  const tripHospital = selectedTrip ? hospitals.find((hospital) => hospital.id === selectedTrip.hospitalId) : null;
  const routePath = tripIncident && tripAmbulance && tripHospital
    ? [
        [tripAmbulance.location.latitude, tripAmbulance.location.longitude] as [number, number],
        [tripIncident.location.latitude, tripIncident.location.longitude] as [number, number],
        [tripHospital.location.latitude, tripHospital.location.longitude] as [number, number]
      ]
    : [];

  return (
    <div className={`map-card ${compact ? 'compact-map' : ''}`}>
      <MapContainer className="map" center={[12.9716, 77.5946]} zoom={12} scrollWheelZoom>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {routePath.length > 0 && <Polyline positions={routePath} pathOptions={{ color: '#dc2626', weight: 5, opacity: 0.72 }} />}
        {hospitals.map((hospital) => (
          <CircleMarker
            key={hospital.id}
            center={[hospital.location.latitude, hospital.location.longitude]}
            radius={hospital.id === selectedTrip?.hospitalId ? 13 : 10}
            pathOptions={{ color: hospital.availableBeds === 0 ? '#991b1b' : '#0f766e', fillColor: hospital.availableBeds === 0 ? '#ef4444' : '#14b8a6', fillOpacity: 0.85 }}
          >
            <Popup>
              <strong>{hospital.name}</strong>
              <br />
              {hospital.availableBeds}/{hospital.totalBeds} beds
            </Popup>
          </CircleMarker>
        ))}
        {ambulances.map((ambulance) => (
          <CircleMarker
            key={ambulance.id}
            center={[ambulance.location.latitude, ambulance.location.longitude]}
            radius={ambulance.id === selectedTrip?.ambulanceId ? 11 : ambulance.status === 'AVAILABLE' ? 8 : 6}
            pathOptions={{ color: '#1d4ed8', fillColor: ambulance.status === 'AVAILABLE' ? '#3b82f6' : '#94a3b8', fillOpacity: 0.9 }}
          >
            <Popup>
              <strong>{ambulance.callSign}</strong>
              <br />
              {ambulance.type} - {formatStatus(ambulance.status)}
            </Popup>
          </CircleMarker>
        ))}
        {incidents.map((incident) => (
          <CircleMarker
            key={incident.id}
            center={[incident.location.latitude, incident.location.longitude]}
            radius={incident.id === selectedIncidentId ? 13 : 9}
            pathOptions={{ color: '#991b1b', fillColor: incident.status === 'NEW' ? '#ef4444' : '#f59e0b', fillOpacity: 0.9 }}
          >
            <Popup>
              <strong>{incident.patientName}</strong>
              <br />
              {incident.condition} - {incident.priority}
            </Popup>
          </CircleMarker>
        ))}
      </MapContainer>
    </div>
  );
}

function roleFromPath(pathname: string): Role {
  const role = pathname.replace('/', '') as Role;
  return roles.some((candidate) => candidate.id === role) ? role : 'control';
}

function rolePath(role: Role) {
  return `/${role}`;
}

function formatStatus(value: string) {
  return value.toLowerCase().split('_').map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(' ');
}
