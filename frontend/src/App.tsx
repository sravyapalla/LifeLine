import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Activity, Ambulance as AmbulanceIcon, BellPlus, Hospital as HospitalIcon, RefreshCw, Route, Siren } from 'lucide-react';
import { CircleMarker, MapContainer, Popup, TileLayer } from 'react-leaflet';
import { createIncident, dispatchIncident, getDashboardData, resetDemo } from './api';
import type {
  Ambulance,
  CreateIncidentPayload,
  DispatchResponse,
  EmergencyCondition,
  Hospital,
  Incident,
  IncidentPriority,
  Metrics,
  Trip
} from './types';

interface DashboardState {
  ambulances: Ambulance[];
  hospitals: Hospital[];
  incidents: Incident[];
  trips: Trip[];
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

export default function App() {
  const [data, setData] = useState<DashboardState | null>(null);
  const [selectedIncidentId, setSelectedIncidentId] = useState<string>('');
  const [form, setForm] = useState<CreateIncidentPayload>(initialIncident);
  const [lastDecision, setLastDecision] = useState<DispatchResponse | null>(null);
  const [error, setError] = useState<string>('');
  const [busy, setBusy] = useState(false);

  async function load() {
    setError('');
    const nextData = await getDashboardData();
    setData(nextData);
    const firstOpenIncident = nextData.incidents.find((incident) => incident.status === 'NEW');
    setSelectedIncidentId((current) => current || firstOpenIncident?.id || nextData.incidents[0]?.id || '');
  }

  useEffect(() => {
    load().catch((loadError: Error) => setError(loadError.message));
  }, []);

  const selectedIncident = useMemo(
    () => data?.incidents.find((incident) => incident.id === selectedIncidentId),
    [data?.incidents, selectedIncidentId]
  );

  async function handleCreateIncident() {
    setBusy(true);
    setError('');
    try {
      const incident = await createIncident(form);
      await load();
      setSelectedIncidentId(incident.id);
    } catch (createError) {
      setError((createError as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleDispatch() {
    if (!selectedIncident) return;
    setBusy(true);
    setError('');
    try {
      const decision = await dispatchIncident(selectedIncident.id);
      setLastDecision(decision);
      await load();
      setSelectedIncidentId(decision.incident.id);
    } catch (dispatchError) {
      setError((dispatchError as Error).message);
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
      await load();
    } catch (resetError) {
      setError((resetError as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const metrics = data?.metrics;

  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">LifeLine V1</p>
          <h1>Emergency Dispatch Console</h1>
        </div>
        <div className="actions">
          <button className="icon-button" type="button" onClick={() => load().catch((loadError: Error) => setError(loadError.message))} aria-label="Refresh dashboard" title="Refresh dashboard">
            <RefreshCw size={18} />
          </button>
          <button className="ghost-button" type="button" onClick={handleReset} disabled={busy}>
            Reset
          </button>
        </div>
      </header>

      {error && <div className="alert">{error}</div>}

      <section className="metrics-grid">
        <MetricCard icon={<Siren size={18} />} label="Open" value={metrics?.openIncidents ?? 0} tone="red" />
        <MetricCard icon={<AmbulanceIcon size={18} />} label="Ambulances" value={metrics?.availableAmbulances ?? 0} tone="green" />
        <MetricCard icon={<Route size={18} />} label="Trips" value={metrics?.activeTrips ?? 0} tone="blue" />
        <MetricCard icon={<HospitalIcon size={18} />} label="Bed Avg" value={`${metrics?.averageBedAvailabilityPercent ?? 0}%`} tone="amber" />
      </section>

      <section className="workspace">
        <aside className="panel incident-panel">
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
                  <small>{incident.condition} · {incident.priority}</small>
                </span>
                <em>{incident.status}</em>
              </button>
            ))}
          </div>

          <div className="create-form">
            <div className="panel-heading compact">
              <h2>New Call</h2>
              <BellPlus size={18} />
            </div>
            <label>
              Patient
              <input value={form.patientName} onChange={(event) => setForm({ ...form, patientName: event.target.value })} />
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
            <button className="primary-button" type="button" onClick={handleCreateIncident} disabled={busy}>
              Create Incident
            </button>
          </div>
        </aside>

        <section className="map-stage">
          <MapPanel
            ambulances={data?.ambulances ?? []}
            hospitals={data?.hospitals ?? []}
            incidents={data?.incidents ?? []}
            selectedIncidentId={selectedIncidentId}
          />
        </section>

        <aside className="panel decision-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Decision</p>
              <h2>Dispatch</h2>
            </div>
            <Activity size={18} />
          </div>

          {selectedIncident ? (
            <div className="selected-call">
              <span className={`status-badge ${selectedIncident.status.toLowerCase()}`}>{selectedIncident.status}</span>
              <h3>{selectedIncident.patientName}</h3>
              <p>{selectedIncident.condition} · {selectedIncident.priority}</p>
              <button className="primary-button dispatch-button" type="button" onClick={handleDispatch} disabled={busy || selectedIncident.status !== 'NEW'}>
                Run Dispatch
              </button>
            </div>
          ) : (
            <div className="empty-state">No incident selected</div>
          )}

          {lastDecision && (
            <div className="decision-result">
              <h3>{lastDecision.ambulance.callSign}</h3>
              <p>{lastDecision.ambulance.type} from {lastDecision.ambulance.baseStation}</p>
              <div className="route-summary">
                <span>{lastDecision.trip.pickupEtaMinutes} min pickup</span>
                <span>{lastDecision.trip.hospitalEtaMinutes} min transfer</span>
              </div>
              <h3>{lastDecision.hospital.name}</h3>
              <p>{lastDecision.hospital.availableBeds} beds after reservation</p>
              <div className="score-box">
                <strong>Score {lastDecision.winningScore.totalCost}</strong>
                <small>{lastDecision.winningScore.explanation}</small>
              </div>
              <div className="candidate-list">
                <h4>Top Candidates</h4>
                {lastDecision.alternatives.slice(0, 5).map((candidate, index) => (
                  <div className="candidate-row" key={`${candidate.ambulanceId}-${candidate.hospitalId}`}>
                    <span>{index + 1}</span>
                    <div>
                      <strong>{candidate.ambulanceId} → {candidate.hospitalId}</strong>
                      <small>{candidate.explanation}</small>
                    </div>
                    <em>{candidate.totalCost}</em>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="resource-list">
            <h3>Resources</h3>
            {data?.ambulances.map((ambulance) => (
              <div className="resource-row" key={ambulance.id}>
                <span className={`resource-dot ${ambulance.status.toLowerCase()}`} />
                <span>{ambulance.callSign}</span>
                <em>{ambulance.type}</em>
              </div>
            ))}
          </div>
        </aside>
      </section>
    </main>
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
  selectedIncidentId
}: {
  ambulances: Ambulance[];
  hospitals: Hospital[];
  incidents: Incident[];
  selectedIncidentId: string;
}) {
  return (
    <div className="map-card">
      <MapContainer className="map" center={[12.9716, 77.5946]} zoom={12} scrollWheelZoom>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {hospitals.map((hospital) => (
          <CircleMarker
            key={hospital.id}
            center={[hospital.location.latitude, hospital.location.longitude]}
            radius={10}
            pathOptions={{ color: '#0f766e', fillColor: '#14b8a6', fillOpacity: 0.85 }}
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
            radius={ambulance.status === 'AVAILABLE' ? 8 : 6}
            pathOptions={{ color: '#1d4ed8', fillColor: ambulance.status === 'AVAILABLE' ? '#3b82f6' : '#94a3b8', fillOpacity: 0.9 }}
          >
            <Popup>
              <strong>{ambulance.callSign}</strong>
              <br />
              {ambulance.type} · {ambulance.status}
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
              {incident.condition} · {incident.priority}
            </Popup>
          </CircleMarker>
        ))}
      </MapContainer>
    </div>
  );
}
