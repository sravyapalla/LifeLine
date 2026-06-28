import { useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  Activity,
  Ambulance as AmbulanceIcon,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Bed,
  BellPlus,
  CheckCircle2,
  ClipboardList,
  HeartPulse,
  Hospital as HospitalIcon,
  LogOut,
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
  acknowledgeNotification,
  approveHospitalApplication,
  approveUser,
  clearAuthToken,
  createHospitalApplication,
  createIncident,
  dispatchIncident,
  getAuditEvents,
  getCurrentUser,
  getDashboardData,
  getHospitalApplications,
  getPendingUserApprovals,
  getPlatformServices,
  getStoredAuthToken,
  login,
  publishOutboxEvents,
  rerouteTrip,
  resetDemo,
  runSimulation,
  signup,
  updateAmbulanceLocation,
  updateHospitalCapacity,
  updateTripStatus
} from './api';
import type {
  Ambulance,
  AmbulanceLocationSnapshot,
  AuthenticatedUser,
  CreateIncidentPayload,
  CreateHospitalApplicationPayload,
  DispatchAuditRecord,
  DispatchResponse,
  EmergencyCondition,
  Hospital,
  HospitalApplication,
  Incident,
  IncidentPriority,
  Metrics,
  Notification,
  NotificationRole,
  OptimizationStrategy,
  OutboxEvent,
  OutboxPublishResponse,
  OutboxSummary,
  PlatformServiceDescriptor,
  SimulationAssignment,
  SimulationRequestPayload,
  SimulationResult,
  SimulationStrategyResult,
  SecurityAuditEvent,
  SignupRequest,
  Trip,
  TripStatus
} from './types';

type Role = 'patient' | 'driver' | 'hospital' | 'control' | 'simulation';

interface DashboardState {
  ambulances: Ambulance[];
  liveLocations: AmbulanceLocationSnapshot[];
  hospitals: Hospital[];
  incidents: Incident[];
  trips: Trip[];
  dispatchDecisions: DispatchAuditRecord[];
  outboxEvents: OutboxEvent[];
  outboxSummary: OutboxSummary;
  metrics: Metrics;
  notifications: Notification[];
  simulations: SimulationResult[];
  hospitalApplications: HospitalApplication[];
  pendingUserApprovals: AuthenticatedUser[];
}

const initialIncident: CreateIncidentPayload = {
  patientName: 'New Patient',
  phone: '+91-90000-12345',
  condition: 'CARDIAC',
  priority: 'CRITICAL',
  addressText: 'Koramangala 5th Block, Bengaluru',
  landmark: 'Near Forum signal',
  locationSource: 'MANUAL',
  latitude: 12.9458,
  longitude: 77.6309
};

const initialHospitalApplication: CreateHospitalApplicationPayload = {
  hospitalName: 'New Partner Hospital',
  contactName: 'Operations Lead',
  contactPhone: '+91-90000-22222',
  addressText: 'Indiranagar, Bengaluru',
  latitude: 12.9784,
  longitude: 77.6408,
  specialties: ['GENERAL', 'TRAUMA'],
  totalBeds: 20
};

const conditions: EmergencyCondition[] = ['CARDIAC', 'TRAUMA', 'PEDIATRIC', 'STROKE', 'GENERAL'];
const priorities: IncidentPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const strategies: OptimizationStrategy[] = ['GREEDY_SEQUENTIAL', 'GLOBAL_MIN_COST'];

const roles: { id: Role; label: string; icon: ReactNode }[] = [
  { id: 'patient', label: 'Patient', icon: <UserRound size={17} /> },
  { id: 'driver', label: 'Ambulance', icon: <AmbulanceIcon size={17} /> },
  { id: 'hospital', label: 'Hospital', icon: <HospitalIcon size={17} /> },
  { id: 'control', label: 'Control', icon: <RadioTower size={17} /> },
  { id: 'simulation', label: 'Simulation', icon: <Activity size={17} /> }
];

const activeTripStatuses: TripStatus[] = ['RESERVED', 'EN_ROUTE_PATIENT', 'EN_ROUTE_HOSPITAL'];
const initialSimulationRequest: SimulationRequestPayload = {
  incidentCount: 6,
  randomSeed: 20260621,
  criticalRatio: 0.35,
  ambulanceOutages: [],
  exhaustedHospitals: [],
  capacityStressPercent: 15,
  strategy: 'GLOBAL_MIN_COST'
};

export default function App() {
  const [role, setRole] = useState<Role>(() => roleFromPath(window.location.pathname));
  const [authReady, setAuthReady] = useState(false);
  const [user, setUser] = useState<AuthenticatedUser | null>(null);
  const [loginForm, setLoginForm] = useState({ username: '', password: '' });
  const [signupForm, setSignupForm] = useState<SignupRequest>({ email: '', displayName: '', password: '', role: 'PATIENT' });
  const [loginError, setLoginError] = useState('');
  const [hospitalApplicationForm, setHospitalApplicationForm] = useState<CreateHospitalApplicationPayload>(initialHospitalApplication);
  const [hospitalApplicationMessage, setHospitalApplicationMessage] = useState('');
  const [data, setData] = useState<DashboardState | null>(null);
  const [selectedIncidentId, setSelectedIncidentId] = useState('');
  const [selectedTripId, setSelectedTripId] = useState('');
  const [form, setForm] = useState<CreateIncidentPayload>(initialIncident);
  const [capacityDrafts, setCapacityDrafts] = useState<Record<string, number>>({});
  const [lastDecision, setLastDecision] = useState<DispatchResponse | null>(null);
  const [lastOutboxPublish, setLastOutboxPublish] = useState<OutboxPublishResponse | null>(null);
  const [simulationForm, setSimulationForm] = useState<SimulationRequestPayload>(initialSimulationRequest);
  const [simulationResult, setSimulationResult] = useState<SimulationResult | null>(null);
  const [auditEvents, setAuditEvents] = useState<SecurityAuditEvent[]>([]);
  const [platformServices, setPlatformServices] = useState<PlatformServiceDescriptor[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [driverWatchId, setDriverWatchId] = useState<number | null>(null);
  const [driverLocationStatus, setDriverLocationStatus] = useState('GPS sharing is off');

  async function load(currentUser = user, currentRole = role) {
    if (!currentUser) return;
    setError('');
    const nextRole = roleFromPath(rolePath(currentRole), currentUser);
    const nextData = await getDashboardData(notificationRoleFor(nextRole), currentUser.role);
    const nextAuditEvents = currentUser.role === 'CONTROL' ? await getAuditEvents() : [];
    const nextPlatformServices = currentUser.role === 'CONTROL' ? await loadPlatformServices() : [];
    const [hospitalApplications, pendingUserApprovals] = currentUser.role === 'CONTROL'
      ? await Promise.all([getHospitalApplications(), getPendingUserApprovals()])
      : [[], []];
    setData({ ...nextData, hospitalApplications, pendingUserApprovals });
    setAuditEvents(nextAuditEvents);
    setPlatformServices(nextPlatformServices);
    setSimulationResult((current) => current ?? nextData.simulations[0] ?? null);
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
    const token = getStoredAuthToken();
    if (!token) {
      setAuthReady(true);
      return;
    }

    getCurrentUser()
      .then((nextUser) => {
        const nextRole = roleFromPath(window.location.pathname, nextUser);
        setUser(nextUser);
        setRole(nextRole);
        window.history.replaceState(null, '', rolePath(nextRole));
      })
      .catch(() => {
        clearAuthToken();
        setUser(null);
      })
      .finally(() => setAuthReady(true));
  }, []);

  useEffect(() => {
    if (!authReady || !user) return;
    load().catch((loadError: Error) => setError(loadError.message));
  }, [authReady, role, user?.username]);

  useEffect(() => {
    return () => {
      if (driverWatchId !== null && 'geolocation' in navigator) {
        navigator.geolocation.clearWatch(driverWatchId);
      }
    };
  }, [driverWatchId]);

  useEffect(() => {
    function syncPath() {
      if (!user) return;
      const nextRole = roleFromPath(window.location.pathname, user);
      setRole(nextRole);
      window.history.replaceState(null, '', rolePath(nextRole));
    }

    window.addEventListener('popstate', syncPath);
    return () => window.removeEventListener('popstate', syncPath);
  }, [user]);

  const selectedIncident = useMemo(
    () => data?.incidents.find((incident) => incident.id === selectedIncidentId) ?? null,
    [data?.incidents, selectedIncidentId]
  );

  const selectedTrip = useMemo(
    () => data?.trips.find((trip) => trip.id === selectedTripId) ?? null,
    [data?.trips, selectedTripId]
  );

  function navigateRole(nextRole: Role) {
    if (!user || !allowedRolesForUser(user).includes(nextRole)) return;
    setRole(nextRole);
    window.history.pushState(null, '', rolePath(nextRole));
  }

  async function handleLogin() {
    setBusy(true);
    setLoginError('');
    try {
      const response = await login(loginForm);
      const nextRole = roleFromPath(window.location.pathname, response.user);
      setUser(response.user);
      setRole(nextRole);
      setError('');
      window.history.replaceState(null, '', rolePath(nextRole));
    } catch (authError) {
      setLoginError((authError as Error).message);
    } finally {
      setBusy(false);
      setAuthReady(true);
    }
  }

  async function handleSignup() {
    setBusy(true);
    setLoginError('');
    try {
      const response = await signup(signupForm);
      if (signupForm.role === 'HOSPITAL') {
        await createHospitalApplication({
          ...hospitalApplicationForm,
          contactName: hospitalApplicationForm.contactName || signupForm.displayName,
          contactPhone: hospitalApplicationForm.contactPhone
        });
      }
      const nextRole = roleFromPath(window.location.pathname, response.user);
      setUser(response.user);
      setRole(nextRole);
      setError('');
      window.history.replaceState(null, '', rolePath(nextRole));
    } catch (authError) {
      setLoginError((authError as Error).message);
    } finally {
      setBusy(false);
      setAuthReady(true);
    }
  }

  function handleLogout() {
    clearAuthToken();
    setUser(null);
    setData(null);
    setAuditEvents([]);
    setPlatformServices([]);
    setSelectedIncidentId('');
    setSelectedTripId('');
    setLastDecision(null);
    setLastOutboxPublish(null);
    setSimulationResult(null);
    if (driverWatchId !== null && 'geolocation' in navigator) {
      navigator.geolocation.clearWatch(driverWatchId);
    }
    setDriverWatchId(null);
    setDriverLocationStatus('GPS sharing is off');
    setRole('patient');
    window.history.replaceState(null, '', '/');
  }

  async function handleCreateIncident() {
    setBusy(true);
    setError('');
    try {
      const incident = await createIncident(form);
      await load();
      setSelectedIncidentId(incident.id);
      if (user?.role === 'CONTROL') {
        navigateRole('control');
      }
    } catch (createError) {
      setError((createError as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleHospitalApplicationSubmit() {
    setBusy(true);
    setHospitalApplicationMessage('');
    setLoginError('');
    try {
      const application = await createHospitalApplication(hospitalApplicationForm);
      setHospitalApplicationMessage(`Application ${application.id} submitted for control review.`);
      setHospitalApplicationForm(initialHospitalApplication);
    } catch (applicationError) {
      setHospitalApplicationMessage((applicationError as Error).message);
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

  async function handleMoveAmbulance(ambulance: Ambulance, deltaLatitude: number, deltaLongitude: number) {
    setBusy(true);
    setError('');
    try {
      await updateAmbulanceLocation(ambulance.id, {
        latitude: Number((ambulance.location.latitude + deltaLatitude).toFixed(5)),
        longitude: Number((ambulance.location.longitude + deltaLongitude).toFixed(5))
      });
      await load();
    } catch (locationError) {
      setError((locationError as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function handleStartDriverTracking(ambulance: Ambulance) {
    if (!('geolocation' in navigator)) {
      setDriverLocationStatus('Browser GPS is not available');
      return;
    }
    if (driverWatchId !== null) {
      navigator.geolocation.clearWatch(driverWatchId);
    }
    const watchId = navigator.geolocation.watchPosition(
      async (position) => {
        try {
          await updateAmbulanceLocation(ambulance.id, {
            latitude: Number(position.coords.latitude.toFixed(5)),
            longitude: Number(position.coords.longitude.toFixed(5))
          });
          setDriverLocationStatus(`GPS shared ${new Date().toLocaleTimeString()}`);
          await load();
        } catch (locationError) {
          setDriverLocationStatus((locationError as Error).message);
        }
      },
      (locationError) => setDriverLocationStatus(locationError.message),
      { enableHighAccuracy: true, maximumAge: 10000, timeout: 15000 }
    );
    setDriverWatchId(watchId);
    setDriverLocationStatus('GPS sharing is on');
  }

  function handleStopDriverTracking() {
    if (driverWatchId !== null && 'geolocation' in navigator) {
      navigator.geolocation.clearWatch(driverWatchId);
    }
    setDriverWatchId(null);
    setDriverLocationStatus('GPS sharing is off');
  }

  async function handleApproveHospitalApplication(applicationId: string) {
    setBusy(true);
    setError('');
    try {
      await approveHospitalApplication(applicationId);
      await load();
    } catch (approveError) {
      setError((approveError as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleApproveUser(username: string) {
    setBusy(true);
    setError('');
    try {
      await approveUser(username);
      await load();
    } catch (approveError) {
      setError((approveError as Error).message);
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

  async function handlePublishOutbox() {
    setBusy(true);
    setError('');
    try {
      const result = await publishOutboxEvents();
      setLastOutboxPublish(result);
      await load();
    } catch (publishError) {
      setError((publishError as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleAcknowledgeNotification(notificationId: string) {
    setBusy(true);
    setError('');
    try {
      await acknowledgeNotification(notificationId);
      await load();
    } catch (notificationError) {
      setError((notificationError as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function handleRunSimulation() {
    setBusy(true);
    setError('');
    try {
      const result = await runSimulation(simulationForm);
      setSimulationResult(result);
      await load();
      navigateRole('simulation');
    } catch (simulationError) {
      setError((simulationError as Error).message);
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
      setLastOutboxPublish(null);
      setSimulationResult(null);
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
  const visibleRoles = user ? roles.filter((item) => allowedRolesForUser(user).includes(item.id)) : [];

  if (!authReady) {
    return (
      <main className="shell">
        <div className="loading-panel">Loading LifeLine...</div>
      </main>
    );
  }

  if (!user) {
    return (
      <LoginView
        form={loginForm}
        setForm={setLoginForm}
        onLogin={handleLogin}
        signupForm={signupForm}
        setSignupForm={setSignupForm}
        onSignup={handleSignup}
        hospitalApplicationForm={hospitalApplicationForm}
        setHospitalApplicationForm={setHospitalApplicationForm}
        onHospitalApplicationSubmit={handleHospitalApplicationSubmit}
        hospitalApplicationMessage={hospitalApplicationMessage}
        busy={busy}
        error={loginError}
      />
    );
  }

  return (
    <main className="shell">
      <header className="topbar">
        <div className="brand-block">
          <p className="eyebrow">Emergency Response</p>
          <h1>LifeLine</h1>
        </div>

        <nav className="role-tabs" aria-label="Role navigation">
          {visibleRoles.map((item) => (
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
          <div className="user-badge">
            <strong>{user.displayName}</strong>
            <span>{approvalRoleLabel(user.role)}</span>
          </div>
          <button className="icon-button" type="button" onClick={() => load().catch((loadError: Error) => setError(loadError.message))} aria-label="Refresh dashboard" title="Refresh dashboard">
            <RefreshCw size={18} />
          </button>
          {user.role === 'CONTROL' && <button className="ghost-button" type="button" onClick={handleReset} disabled={busy}>
            <RotateCcw size={16} />
            Reset
          </button>}
          <button className="icon-button" type="button" onClick={handleLogout} aria-label="Log out" title="Log out">
            <LogOut size={18} />
          </button>
        </div>
      </header>

      {error && <div className="alert">{error}</div>}

      {user.role === 'CONTROL' && (
        <section className="metrics-grid">
          <MetricCard icon={<Siren size={18} />} label="Open" value={metrics?.openIncidents ?? 0} tone="red" />
          <MetricCard icon={<AmbulanceIcon size={18} />} label="Available" value={metrics?.availableAmbulances ?? 0} tone="green" />
          <MetricCard icon={<Route size={18} />} label="Active Trips" value={activeTrips.length} tone="blue" />
          <MetricCard icon={<Bed size={18} />} label="Bed Avg" value={`${metrics?.averageBedAvailabilityPercent ?? 0}%`} tone="amber" />
          <MetricCard icon={<ClipboardList size={18} />} label="Pending Events" value={metrics?.pendingOutboxEvents ?? 0} tone="violet" />
          <MetricCard icon={<Activity size={18} />} label="Sim Runs" value={metrics?.simulationRuns ?? data?.simulations.length ?? 0} tone="blue" />
          <MetricCard icon={<Route size={18} />} label="Opt Gain" value={`${metrics?.latestOptimizationImprovementPercent ?? 0}%`} tone="green" />
          <MetricCard icon={<MapPin size={18} />} label="Live Locs" value={metrics?.liveAmbulanceLocations ?? 0} tone="amber" />
          <MetricCard icon={<BellPlus size={18} />} label="Notify" value={metrics?.notificationBacklog ?? 0} tone="red" />
          <MetricCard icon={<RadioTower size={18} />} label="Kafka Fail" value={metrics?.kafkaPublishFailures ?? 0} tone="violet" />
        </section>
      )}

      <NotificationPanel
        notifications={data?.notifications ?? []}
        role={role}
        onAcknowledge={handleAcknowledgeNotification}
        busy={busy}
      />

      {user.status !== 'APPROVED' && <PendingAccessView user={user} />}

      {user.status === 'APPROVED' && role === 'patient' && (
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

      {user.status === 'APPROVED' && role === 'driver' && (
        <DriverView
          data={data}
          selectedTripId={selectedTripId}
          setSelectedTripId={setSelectedTripId}
          onTripStatus={handleTripStatus}
          onMoveAmbulance={handleMoveAmbulance}
          onStartTracking={handleStartDriverTracking}
          onStopTracking={handleStopDriverTracking}
          trackingActive={driverWatchId !== null}
          trackingStatus={driverLocationStatus}
          busy={busy}
        />
      )}

      {user.status === 'APPROVED' && role === 'hospital' && (
        <HospitalView
          data={data}
          capacityDrafts={capacityDrafts}
          setCapacityDrafts={setCapacityDrafts}
          onHospitalCapacity={handleHospitalCapacity}
          busy={busy}
        />
      )}

      {user.status === 'APPROVED' && role === 'control' && (
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
          onMoveAmbulance={handleMoveAmbulance}
          onPublishOutbox={handlePublishOutbox}
          lastDecision={lastDecision}
          lastOutboxPublish={lastOutboxPublish}
          auditEvents={auditEvents}
          platformServices={platformServices}
          onApproveHospitalApplication={handleApproveHospitalApplication}
          onApproveUser={handleApproveUser}
          busy={busy}
        />
      )}

      {user.status === 'APPROVED' && role === 'simulation' && (
        <SimulationView
          data={data}
          request={simulationForm}
          setRequest={setSimulationForm}
          result={simulationResult}
          setResult={setSimulationResult}
          onRun={handleRunSimulation}
          busy={busy}
        />
      )}
    </main>
  );
}

async function loadPlatformServices() {
  try {
    const response = await getPlatformServices();
    return response.services;
  } catch {
    return [];
  }
}

function PendingAccessView({ user }: { user: AuthenticatedUser }) {
  return (
    <section className="pending-access panel">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">{formatStatus(user.role)} Signup</p>
          <h2>Approval Pending</h2>
        </div>
        <CheckCircle2 size={18} />
      </div>
      <p>
        Your account was created and is waiting for Control Center approval. Patient accounts are active immediately;
        ambulance and hospital accounts are reviewed before operational access is enabled.
      </p>
    </section>
  );
}

function LoginView({
  form,
  setForm,
  onLogin,
  signupForm,
  setSignupForm,
  onSignup,
  hospitalApplicationForm,
  setHospitalApplicationForm,
  onHospitalApplicationSubmit,
  hospitalApplicationMessage,
  busy,
  error
}: {
  form: { username: string; password: string };
  setForm: (form: { username: string; password: string }) => void;
  onLogin: () => void;
  signupForm: SignupRequest;
  setSignupForm: (form: SignupRequest) => void;
  onSignup: () => void;
  hospitalApplicationForm: CreateHospitalApplicationPayload;
  setHospitalApplicationForm: (form: CreateHospitalApplicationPayload) => void;
  onHospitalApplicationSubmit: () => void;
  hospitalApplicationMessage: string;
  busy: boolean;
  error: string;
}) {
  const [mode, setMode] = useState<'signin' | 'signup'>('signin');

  function useHospitalLocation() {
    if (!('geolocation' in navigator)) return;
    navigator.geolocation.getCurrentPosition((position) => {
      setHospitalApplicationForm({
        ...hospitalApplicationForm,
        latitude: Number(position.coords.latitude.toFixed(5)),
        longitude: Number(position.coords.longitude.toFixed(5))
      });
    });
  }

  return (
    <main className="login-shell">
      <section className="login-panel">
        <div className="brand-block">
          <p className="eyebrow">Emergency Response</p>
          <h1>LifeLine</h1>
        </div>

        <div className="auth-mode-toggle">
          <button className={mode === 'signin' ? 'active' : ''} type="button" onClick={() => setMode('signin')}>Sign in</button>
          <button className={mode === 'signup' ? 'active' : ''} type="button" onClick={() => setMode('signup')}>Sign up</button>
        </div>

        <div className="login-form">
          {mode === 'signin' ? (
            <>
              <label>
                Email
                <input value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} />
              </label>
              <label>
                Password
                <input type="password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} />
              </label>
              <button className="primary-button" type="button" onClick={onLogin} disabled={busy}>
                Sign In
              </button>
            </>
          ) : (
            <>
              <label>
                Name
                <input value={signupForm.displayName} onChange={(event) => setSignupForm({ ...signupForm, displayName: event.target.value })} />
              </label>
              <label>
                Email
                <input value={signupForm.email} onChange={(event) => setSignupForm({ ...signupForm, email: event.target.value })} />
              </label>
              <label>
                Password
                <input type="password" value={signupForm.password} onChange={(event) => setSignupForm({ ...signupForm, password: event.target.value })} />
              </label>
              <div className="signup-role-grid">
                {(['PATIENT', 'DRIVER', 'HOSPITAL'] as SignupRequest['role'][]).map((roleOption) => (
                  <button
                    key={roleOption}
                    className={`role-tab ${signupForm.role === roleOption ? 'active' : ''}`}
                    type="button"
                    onClick={() => setSignupForm({ ...signupForm, role: roleOption })}
                  >
                    {approvalRoleLabel(roleOption)}
                  </button>
                ))}
              </div>
              <button className="primary-button" type="button" onClick={onSignup} disabled={busy}>
                Create Account
              </button>
            </>
          )}
          {error && <div className="alert compact-alert">{error}</div>}
        </div>

        {mode === 'signup' && signupForm.role === 'HOSPITAL' && <div className="enrollment-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Hospital Profile</p>
              <h2>Care Network Details</h2>
            </div>
            <HospitalIcon size={18} />
          </div>
          <div className="create-form open">
            <label>
              Hospital
              <input value={hospitalApplicationForm.hospitalName} onChange={(event) => setHospitalApplicationForm({ ...hospitalApplicationForm, hospitalName: event.target.value })} />
            </label>
            <label>
              Contact
              <input value={hospitalApplicationForm.contactName} onChange={(event) => setHospitalApplicationForm({ ...hospitalApplicationForm, contactName: event.target.value })} />
            </label>
            <label>
              Phone
              <input value={hospitalApplicationForm.contactPhone} onChange={(event) => setHospitalApplicationForm({ ...hospitalApplicationForm, contactPhone: event.target.value })} />
            </label>
            <label>
              Address
              <input value={hospitalApplicationForm.addressText} onChange={(event) => setHospitalApplicationForm({ ...hospitalApplicationForm, addressText: event.target.value })} />
            </label>
            <div className="location-source-row">
              <span>{hospitalApplicationForm.latitude.toFixed(4)}, {hospitalApplicationForm.longitude.toFixed(4)}</span>
              <button className="ghost-button" type="button" onClick={useHospitalLocation}>
                <Navigation size={15} />
                Use Current Location
              </button>
            </div>
            <SpecialtyPicker
              selected={hospitalApplicationForm.specialties}
              onChange={(specialties) => setHospitalApplicationForm({ ...hospitalApplicationForm, specialties })}
            />
            <label>
              Beds
              <input type="number" min={1} value={hospitalApplicationForm.totalBeds} onChange={(event) => setHospitalApplicationForm({ ...hospitalApplicationForm, totalBeds: Number(event.target.value) })} />
            </label>
            <button className="secondary-button" type="button" onClick={onHospitalApplicationSubmit} disabled={busy}>
              Submit for Review
            </button>
            {hospitalApplicationMessage && <p className="form-note">{hospitalApplicationMessage}</p>}
          </div>
        </div>}
      </section>
    </main>
  );
}

function SpecialtyPicker({ selected, onChange }: { selected: EmergencyCondition[]; onChange: (selected: EmergencyCondition[]) => void }) {
  return (
    <div className="specialty-picker">
      <span>Specialties</span>
      <div>
        {conditions.map((condition) => {
          const checked = selected.includes(condition);
          return (
            <label key={condition} className={`chip-checkbox ${checked ? 'checked' : ''}`}>
              <input
                type="checkbox"
                checked={checked}
                onChange={() => onChange(checked ? selected.filter((item) => item !== condition) : [...selected, condition])}
              />
              {condition}
            </label>
          );
        })}
      </div>
    </div>
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
  const availableAmbulances = data?.ambulances.filter((ambulance) => ambulance.status === 'AVAILABLE') ?? [];
  const availableHospitals = data?.hospitals.filter((hospital) => hospital.availableBeds > 0) ?? [];

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

        <PatientCoveragePanel
          ambulances={availableAmbulances}
          hospitals={availableHospitals}
          patientLocation={{ latitude: form.latitude, longitude: form.longitude }}
        />
      </aside>

      <section className="map-stage">
        <MapPanel
          ambulances={data?.ambulances ?? []}
          liveLocations={data?.liveLocations ?? []}
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
            {selectedTrip && <PatientTrackingPanel trip={selectedTrip} ambulance={assignedAmbulance ?? null} hospital={receivingHospital ?? null} />}
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

function PatientCoveragePanel({
  ambulances,
  hospitals,
  patientLocation
}: {
  ambulances: Ambulance[];
  hospitals: Hospital[];
  patientLocation: { latitude: number; longitude: number };
}) {
  const [selectedResource, setSelectedResource] = useState<{ kind: 'ambulance' | 'hospital'; id: string } | null>(null);
  const nearbyAmbulances = [...ambulances].sort((left, right) => distanceKm(patientLocation, left.location) - distanceKm(patientLocation, right.location));
  const nearbyHospitals = [...hospitals].sort((left, right) => distanceKm(patientLocation, left.location) - distanceKm(patientLocation, right.location));
  const visibleAmbulances = nearbyAmbulances.slice(0, 5);
  const visibleHospitals = nearbyHospitals.slice(0, 5);
  const selectedAmbulance = selectedResource?.kind === 'ambulance' ? nearbyAmbulances.find((ambulance) => ambulance.id === selectedResource.id) ?? null : null;
  const selectedHospital = selectedResource?.kind === 'hospital' ? nearbyHospitals.find((hospital) => hospital.id === selectedResource.id) ?? null : null;

  return (
    <div className="coverage-panel">
      <div className="panel-heading compact-heading">
        <div>
          <p className="eyebrow">Coverage</p>
          <h3>Nearest Available</h3>
        </div>
        <MapPin size={18} />
      </div>

      <div className="coverage-grid">
        <article>
          <strong>{ambulances.length}</strong>
          <span>Ambulances</span>
        </article>
        <article>
          <strong>{hospitals.length}</strong>
          <span>Hospitals</span>
        </article>
      </div>

      <div className="coverage-list">
        <div className="coverage-section-heading">
          <span>Ambulances</span>
          <em>Nearest {visibleAmbulances.length} of {nearbyAmbulances.length}</em>
        </div>
        {nearbyAmbulances.length === 0 && <div className="empty-state compact">No ambulances currently available</div>}
        {visibleAmbulances.map((ambulance) => (
          <button
            className={`coverage-row ${selectedResource?.kind === 'ambulance' && selectedResource.id === ambulance.id ? 'selected' : ''}`}
            key={ambulance.id}
            type="button"
            onClick={() => setSelectedResource({ kind: 'ambulance', id: ambulance.id })}
          >
            <span className={`resource-dot ${ambulance.status.toLowerCase()}`} />
            <span>
              <strong>{ambulance.callSign}</strong>
              <small>{ambulance.type} - {formatDistance(distanceKm(patientLocation, ambulance.location))} away</small>
            </span>
          </button>
        ))}

        <div className="coverage-section-heading">
          <span>Hospitals</span>
          <em>Nearest {visibleHospitals.length} of {nearbyHospitals.length}</em>
        </div>
        {nearbyHospitals.length === 0 && <div className="empty-state compact">No hospitals currently have capacity</div>}
        {visibleHospitals.map((hospital) => (
          <button
            className={`coverage-row ${selectedResource?.kind === 'hospital' && selectedResource.id === hospital.id ? 'selected' : ''}`}
            key={hospital.id}
            type="button"
            onClick={() => setSelectedResource({ kind: 'hospital', id: hospital.id })}
          >
            <span className={`resource-dot ${hospital.availableBeds > 0 ? 'available' : 'offline'}`} />
            <span>
              <strong>{hospital.name}</strong>
              <small>{hospital.availableBeds} beds - {formatDistance(distanceKm(patientLocation, hospital.location))} away</small>
            </span>
          </button>
        ))}
      </div>

      {(selectedAmbulance || selectedHospital) && (
        <div className="coverage-detail">
          {selectedAmbulance && (
            <>
              <strong>{selectedAmbulance.callSign}</strong>
              <InfoLine label="Type" value={selectedAmbulance.type} />
              <InfoLine label="Station" value={selectedAmbulance.baseStation} />
              <InfoLine label="Distance" value={formatDistance(distanceKm(patientLocation, selectedAmbulance.location))} />
              <InfoLine label="Coordinates" value={formatCoordinates(selectedAmbulance.location)} />
            </>
          )}
          {selectedHospital && (
            <>
              <strong>{selectedHospital.name}</strong>
              <InfoLine label="Beds" value={`${selectedHospital.availableBeds}/${selectedHospital.totalBeds}`} />
              <InfoLine label="Specialties" value={selectedHospital.specialties.join(', ')} />
              <InfoLine label="Distance" value={formatDistance(distanceKm(patientLocation, selectedHospital.location))} />
              <InfoLine label="Coordinates" value={formatCoordinates(selectedHospital.location)} />
            </>
          )}
        </div>
      )}
    </div>
  );
}

function PatientTrackingPanel({ trip, ambulance, hospital }: { trip: Trip; ambulance: Ambulance | null; hospital: Hospital | null }) {
  return (
    <div className="tracking-panel">
      <InfoLine label="Pickup ETA" value={`${trip.pickupEtaMinutes} min`} />
      <InfoLine label="Transfer ETA" value={`${trip.hospitalEtaMinutes} min`} />
      {ambulance && <InfoLine label="Unit" value={ambulance.callSign} />}
      {hospital && <InfoLine label="Receiving" value={hospital.name} />}
    </div>
  );
}

function NotificationPanel({
  notifications,
  role,
  onAcknowledge,
  busy
}: {
  notifications: Notification[];
  role: Role;
  onAcknowledge: (notificationId: string) => void;
  busy: boolean;
}) {
  const unread = notifications.filter((notification) => !notification.acknowledgedAt);

  return (
    <section className="notification-panel" aria-label={`${role} notifications`}>
      <div className="notification-heading">
        <span><BellPlus size={16} /></span>
        <strong>{formatStatus(role)} Notifications</strong>
        <em>{unread.length} unread</em>
      </div>
      <div className="notification-list">
        {notifications.length === 0 && <div className="empty-state compact">No notifications</div>}
        {notifications.slice(0, 4).map((notification) => (
          <article className={`notification-item ${notification.acknowledgedAt ? 'read' : ''}`} key={notification.id}>
            <div>
              <strong>{notification.title}</strong>
              <small>{notification.message}</small>
              <small>{notification.eventType} - {formatRelativeTime(notification.createdAt)}</small>
            </div>
            <button
              className="ghost-button"
              type="button"
              onClick={() => onAcknowledge(notification.id)}
              disabled={busy || Boolean(notification.acknowledgedAt)}
            >
              Ack
            </button>
          </article>
        ))}
      </div>
    </section>
  );
}

function DriverView({
  data,
  selectedTripId,
  setSelectedTripId,
  onTripStatus,
  onMoveAmbulance,
  onStartTracking,
  onStopTracking,
  trackingActive,
  trackingStatus,
  busy
}: {
  data: DashboardState | null;
  selectedTripId: string;
  setSelectedTripId: (id: string) => void;
  onTripStatus: (tripId: string, status: TripStatus) => void;
  onMoveAmbulance: (ambulance: Ambulance, deltaLatitude: number, deltaLongitude: number) => void;
  onStartTracking: (ambulance: Ambulance) => void;
  onStopTracking: () => void;
  trackingActive: boolean;
  trackingStatus: string;
  busy: boolean;
}) {
  const selectedTrip = data?.trips.find((trip) => trip.id === selectedTripId) ?? null;
  const ownAmbulance = data?.ambulances[0] ?? null;
  const assignedTrips = data?.trips ?? [];
  const receivingHospitals = data?.hospitals.filter((hospital) => hospital.availableBeds > 0) ?? [];

  return (
    <section className="role-layout driver-layout">
      <aside className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Ambulance</p>
            <h2>My Unit</h2>
          </div>
          <AmbulanceIcon size={18} />
        </div>

        {ownAmbulance ? (
          <DriverUnitPanel
            ambulance={ownAmbulance}
            liveLocation={data?.liveLocations.find((location) => location.ambulanceId === ownAmbulance.id) ?? null}
            activeTrip={selectedTrip}
            onMoveAmbulance={onMoveAmbulance}
            onStartTracking={onStartTracking}
            onStopTracking={onStopTracking}
            trackingActive={trackingActive}
            trackingStatus={trackingStatus}
            busy={busy}
          />
        ) : (
          <div className="empty-state">No ambulance assigned</div>
        )}

        {data && data.ambulances.length > 1 && (
          <div className="resource-list bordered">
            <h3>Nearby Units</h3>
            {data.ambulances.slice(1, 4).map((ambulance) => (
            <LiveAmbulanceRow
              key={ambulance.id}
              ambulance={ambulance}
              liveLocation={data.liveLocations.find((location) => location.ambulanceId === ambulance.id) ?? null}
              onMoveAmbulance={onMoveAmbulance}
              busy={busy}
            />
            ))}
          </div>
        )}
      </aside>

      <section className="panel wide-panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Requests</p>
            <h2>Patient Assignments</h2>
          </div>
          <span className="count">{assignedTrips.length}</span>
        </div>

        {assignedTrips.length === 0 && <div className="empty-state">No assigned patient requests</div>}
        <div className="trip-grid">
          {assignedTrips.map((trip) => (
            <TripCard
              key={trip.id}
              trip={trip}
              data={data!}
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
            <p className="eyebrow">Receiving</p>
            <h2>Hospitals</h2>
          </div>
          <HospitalIcon size={18} />
        </div>

        <DriverHospitalPanel hospitals={receivingHospitals} selectedTrip={selectedTrip} />

        <MapPanel
          ambulances={data?.ambulances ?? []}
          liveLocations={data?.liveLocations ?? []}
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

function DriverUnitPanel({
  ambulance,
  liveLocation,
  activeTrip,
  onMoveAmbulance,
  onStartTracking,
  onStopTracking,
  trackingActive,
  trackingStatus,
  busy
}: {
  ambulance: Ambulance;
  liveLocation: AmbulanceLocationSnapshot | null;
  activeTrip: Trip | null;
  onMoveAmbulance: (ambulance: Ambulance, deltaLatitude: number, deltaLongitude: number) => void;
  onStartTracking: (ambulance: Ambulance) => void;
  onStopTracking: () => void;
  trackingActive: boolean;
  trackingStatus: string;
  busy: boolean;
}) {
  return (
    <div className="driver-unit">
      <div className="unit-card">
        <span className={`resource-dot ${ambulance.status.toLowerCase()}`} />
        <div>
          <h3>{ambulance.callSign}</h3>
          <p>{ambulance.type} - {formatStatus(ambulance.status)}</p>
        </div>
      </div>
      <InfoLine label="Station" value={ambulance.baseStation} />
      <InfoLine label="Location" value={liveLocation ? `Live ${formatRelativeTime(liveLocation.updatedAt)}` : 'Base snapshot'} />
      {activeTrip && <InfoLine label="Current Trip" value={activeTrip.id} />}
      <div className="gps-controls">
        <button className="secondary-button" type="button" onClick={() => onStartTracking(ambulance)} disabled={busy || trackingActive}>
          <Navigation size={15} />
          Start GPS
        </button>
        <button className="ghost-button" type="button" onClick={onStopTracking} disabled={!trackingActive}>
          Stop
        </button>
      </div>
      <p className="form-note">{trackingStatus}</p>
      <LiveAmbulanceRow ambulance={ambulance} liveLocation={liveLocation} onMoveAmbulance={onMoveAmbulance} busy={busy} />
    </div>
  );
}

function DriverHospitalPanel({ hospitals, selectedTrip }: { hospitals: Hospital[]; selectedTrip: Trip | null }) {
  return (
    <div className="driver-hospital-list">
      {hospitals.length === 0 && <div className="empty-state compact">No receiving capacity visible</div>}
      {hospitals.slice(0, 5).map((hospital) => (
        <article className={`receiving-row ${hospital.id === selectedTrip?.hospitalId ? 'selected' : ''}`} key={hospital.id}>
          <span className={`resource-dot ${hospital.availableBeds > 0 ? 'available' : 'offline'}`} />
          <span>
            <strong>{hospital.name}</strong>
            <small>{hospital.availableBeds}/{hospital.totalBeds} beds - {hospital.specialties.slice(0, 2).join(', ')}</small>
          </span>
        </article>
      ))}
    </div>
  );
}

function HospitalView({
  data,
  capacityDrafts,
  setCapacityDrafts,
  onHospitalCapacity,
  busy
}: {
  data: DashboardState | null;
  capacityDrafts: Record<string, number>;
  setCapacityDrafts: (drafts: Record<string, number>) => void;
  onHospitalCapacity: (hospitalId: string, availableBeds: number) => void;
  busy: boolean;
}) {
  if (!data || data.hospitals.length === 0) {
    return <div className="empty-state">No hospital assigned</div>;
  }

  const hospital = data.hospitals[0];
  const incomingTrips = data.trips.filter((trip) => trip.hospitalId === hospital.id && activeTripStatuses.includes(trip.status));
  const draft = capacityDrafts[hospital.id] ?? hospital.availableBeds;
  const loadPercent = hospital.totalBeds === 0 ? 0 : Math.round(((hospital.totalBeds - hospital.availableBeds) / hospital.totalBeds) * 100);

  return (
    <section className="role-layout hospital-ops-layout">
      <aside className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">{hospital.id}</p>
            <h2>{hospital.name}</h2>
          </div>
          <span className={`capacity-pill ${hospital.availableBeds === 0 ? 'exhausted' : ''}`}>{hospital.availableBeds}/{hospital.totalBeds}</span>
        </div>

        <div className="hospital-load-card">
          <strong>{loadPercent}%</strong>
          <span>Capacity Used</span>
          <div>
            <i style={{ width: `${Math.min(100, loadPercent)}%` }} />
          </div>
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
      </aside>

      <section className="panel wide-panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Receiving</p>
            <h2>Incoming Ambulances</h2>
          </div>
          <span className="count">{incomingTrips.length}</span>
        </div>

        <div className="hospital-incoming-board">
          {incomingTrips.length === 0 && <div className="empty-state">No incoming ambulances</div>}
          {incomingTrips.map((trip) => (
            <HospitalTripRow key={trip.id} trip={trip} data={data} />
          ))}
        </div>
      </section>

      <aside className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Signals</p>
            <h2>Receiving Map</h2>
          </div>
          <MapPin size={18} />
        </div>

        <InfoLine label="Available Beds" value={String(hospital.availableBeds)} />
        <InfoLine label="Incoming Trips" value={String(incomingTrips.length)} />
        <InfoLine label="Specialties" value={hospital.specialties.join(', ')} />
        <MapPanel
          ambulances={data.ambulances}
          liveLocations={data.liveLocations}
          hospitals={data.hospitals}
          incidents={data.incidents}
          trips={data.trips}
          selectedIncidentId={incomingTrips[0]?.incidentId ?? ''}
          selectedTripId={incomingTrips[0]?.id ?? ''}
          compact
        />
      </aside>
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
  onMoveAmbulance,
  onPublishOutbox,
  lastDecision,
  lastOutboxPublish,
  auditEvents,
  platformServices,
  onApproveHospitalApplication,
  onApproveUser,
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
  onMoveAmbulance: (ambulance: Ambulance, deltaLatitude: number, deltaLongitude: number) => void;
  onPublishOutbox: () => void;
  lastDecision: DispatchResponse | null;
  lastOutboxPublish: OutboxPublishResponse | null;
  auditEvents: SecurityAuditEvent[];
  platformServices: PlatformServiceDescriptor[];
  onApproveHospitalApplication: (applicationId: string) => void;
  onApproveUser: (username: string) => void;
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

        <ControlCommandSummary data={data} />

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
          liveLocations={data?.liveLocations ?? []}
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

        <div className="compact-list bordered">
          <h3>Live Fleet</h3>
          {data?.ambulances.slice(0, 4).map((ambulance) => (
            <LiveAmbulanceRow
              key={ambulance.id}
              ambulance={ambulance}
              liveLocation={data.liveLocations.find((location) => location.ambulanceId === ambulance.id) ?? null}
              onMoveAmbulance={onMoveAmbulance}
              busy={busy}
            />
          ))}
        </div>

        <ActiveAmbulancePanel
          ambulances={data?.ambulances ?? []}
          liveLocations={data?.liveLocations ?? []}
          trips={data?.trips ?? []}
        />

        <HospitalApplicationsPanel
          applications={data?.hospitalApplications ?? []}
          onApprove={onApproveHospitalApplication}
          busy={busy}
        />

        <UserApprovalsPanel
          users={data?.pendingUserApprovals ?? []}
          onApprove={onApproveUser}
          busy={busy}
        />

        <OutboxPanel
          pending={data?.metrics.pendingOutboxEvents ?? 0}
          published={data?.metrics.publishedOutboxEvents ?? 0}
          summary={data?.outboxSummary ?? null}
          events={data?.outboxEvents ?? []}
          lastPublish={lastOutboxPublish}
          onPublish={onPublishOutbox}
          busy={busy}
        />

        <Timeline events={data?.outboxEvents ?? []} decisions={data?.dispatchDecisions ?? []} />

        <AuditPanel events={auditEvents} />

        <PlatformServicesPanel services={platformServices} />
      </aside>
    </section>
  );
}

function ActiveAmbulancePanel({
  ambulances,
  liveLocations,
  trips
}: {
  ambulances: Ambulance[];
  liveLocations: AmbulanceLocationSnapshot[];
  trips: Trip[];
}) {
  const activeTripAmbulanceIds = new Set(trips.filter((trip) => activeTripStatuses.includes(trip.status)).map((trip) => trip.ambulanceId));
  const activeAmbulances = ambulances.filter((ambulance) => {
    const liveLocation = liveLocations.find((location) => location.ambulanceId === ambulance.id);
    return activeTripAmbulanceIds.has(ambulance.id) || Boolean(liveLocation && Date.now() - new Date(liveLocation.updatedAt).getTime() < 180000);
  });

  return (
    <div className="compact-list bordered">
      <h3>Active Ambulances</h3>
      {activeAmbulances.length === 0 && <div className="empty-state compact">No fresh active units</div>}
      {activeAmbulances.slice(0, 6).map((ambulance) => {
        const liveLocation = liveLocations.find((location) => location.ambulanceId === ambulance.id);
        return (
          <div className="compact-row" key={ambulance.id}>
            <span className={`resource-dot ${ambulance.status.toLowerCase()}`} />
            <span>
              <strong>{ambulance.callSign}</strong>
              <small>{activeTripAmbulanceIds.has(ambulance.id) ? 'Assigned trip' : `Fresh ${formatRelativeTime(liveLocation!.updatedAt)}`}</small>
            </span>
          </div>
        );
      })}
    </div>
  );
}

function HospitalApplicationsPanel({
  applications,
  onApprove,
  busy
}: {
  applications: HospitalApplication[];
  onApprove: (applicationId: string) => void;
  busy: boolean;
}) {
  const pending = applications.filter((application) => application.status === 'PENDING');
  return (
    <div className="compact-list bordered">
      <h3>Hospital Applications</h3>
      {pending.length === 0 && <div className="empty-state compact">No pending hospital applications</div>}
      {pending.map((application) => (
        <article className="application-row" key={application.id}>
          <span>
            <strong>{application.hospitalName}</strong>
            <small>{application.totalBeds} beds - {application.specialties.join(', ')}</small>
          </span>
          <button className="ghost-button" type="button" onClick={() => onApprove(application.id)} disabled={busy}>
            Approve
          </button>
        </article>
      ))}
    </div>
  );
}

function UserApprovalsPanel({
  users,
  onApprove,
  busy
}: {
  users: AuthenticatedUser[];
  onApprove: (username: string) => void;
  busy: boolean;
}) {
  const ambulanceSignups = users.filter((user) => user.role === 'DRIVER');
  const hospitalSignups = users.filter((user) => user.role === 'HOSPITAL');
  const pending = [...ambulanceSignups, ...hospitalSignups];

  return (
    <div className="compact-list bordered">
      <h3>Operational Signups</h3>
      {pending.length === 0 && <div className="empty-state compact">No pending ambulance or hospital signups</div>}
      {pending.map((user) => (
        <article className="application-row" key={user.username}>
          <span>
            <strong>{user.displayName}</strong>
            <small>{approvalRoleLabel(user.role)} - {user.username}</small>
          </span>
          <button className="ghost-button" type="button" onClick={() => onApprove(user.username)} disabled={busy}>
            Approve
          </button>
        </article>
      ))}
    </div>
  );
}

function ControlCommandSummary({ data }: { data: DashboardState | null }) {
  const openIncidents = data?.incidents.filter((incident) => incident.status === 'NEW').length ?? 0;
  const activeTrips = data?.trips.filter((trip) => activeTripStatuses.includes(trip.status)).length ?? 0;
  const exhaustedHospitals = data?.hospitals.filter((hospital) => hospital.availableBeds === 0).length ?? 0;
  const availableAmbulances = data?.ambulances.filter((ambulance) => ambulance.status === 'AVAILABLE').length ?? 0;

  return (
    <div className="command-summary">
      <article>
        <strong>{openIncidents}</strong>
        <span>Open</span>
      </article>
      <article>
        <strong>{activeTrips}</strong>
        <span>Active</span>
      </article>
      <article>
        <strong>{availableAmbulances}</strong>
        <span>Units</span>
      </article>
      <article className={exhaustedHospitals > 0 ? 'attention' : ''}>
        <strong>{exhaustedHospitals}</strong>
        <span>Exhausted</span>
      </article>
    </div>
  );
}

function SimulationView({
  data,
  request,
  setRequest,
  result,
  setResult,
  onRun,
  busy
}: {
  data: DashboardState | null;
  request: SimulationRequestPayload;
  setRequest: (request: SimulationRequestPayload) => void;
  result: SimulationResult | null;
  setResult: (result: SimulationResult | null) => void;
  onRun: () => void;
  busy: boolean;
}) {
  const greedy = result?.strategyResults.find((candidate) => candidate.strategy === 'GREEDY_SEQUENTIAL') ?? null;
  const global = result?.strategyResults.find((candidate) => candidate.strategy === 'GLOBAL_MIN_COST') ?? null;
  const selectedStrategy = global ?? greedy;
  const simulatedIncidents: Incident[] = (selectedStrategy?.assignments ?? []).map((assignment) => ({
    id: assignment.incidentId,
    patientName: assignment.incidentId,
    phone: '+91-90000-SIM',
    condition: assignment.condition,
    priority: assignment.priority,
    location: assignment.incidentLocation,
    addressText: `Simulation address for ${assignment.incidentId}`,
    landmark: '',
    locationSource: 'SIMULATION',
    createdAt: result?.createdAt ?? new Date().toISOString(),
    status: assignment.matched ? 'ASSIGNED' : 'NEW'
  }));
  const unmatched = selectedStrategy?.assignments.filter((assignment) => !assignment.matched) ?? [];

  return (
    <section className="simulation-layout">
      <aside className="panel simulation-controls">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Scenario</p>
            <h2>Ops Simulator</h2>
          </div>
          <Activity size={18} />
        </div>

        <div className="create-form open">
          <label>
            Incidents
            <input type="number" min={1} max={40} value={request.incidentCount} onChange={(event) => setRequest({ ...request, incidentCount: Number(event.target.value) })} />
          </label>
          <label>
            Seed
            <input type="number" value={request.randomSeed} onChange={(event) => setRequest({ ...request, randomSeed: Number(event.target.value) })} />
          </label>
          <label>
            Critical Ratio
            <input type="range" min={0} max={1} step={0.05} value={request.criticalRatio} onChange={(event) => setRequest({ ...request, criticalRatio: Number(event.target.value) })} />
            <small>{Math.round(request.criticalRatio * 100)}%</small>
          </label>
          <label>
            Capacity Stress
            <input type="range" min={0} max={100} step={5} value={request.capacityStressPercent} onChange={(event) => setRequest({ ...request, capacityStressPercent: Number(event.target.value) })} />
            <small>{request.capacityStressPercent}%</small>
          </label>
          <label>
            Exact Strategy
            <select value={request.strategy} onChange={(event) => setRequest({ ...request, strategy: event.target.value as OptimizationStrategy })}>
              {strategies.map((strategy) => <option key={strategy} value={strategy}>{formatStrategy(strategy)}</option>)}
            </select>
          </label>
        </div>

        <SelectorGroup
          title="Ambulance Outages"
          items={data?.ambulances.map((ambulance) => ({ id: ambulance.id, label: ambulance.callSign })) ?? []}
          selected={request.ambulanceOutages}
          onToggle={(id) => setRequest({ ...request, ambulanceOutages: toggleSelection(request.ambulanceOutages, id) })}
        />

        <SelectorGroup
          title="Exhausted Hospitals"
          items={data?.hospitals.map((hospital) => ({ id: hospital.id, label: hospital.name })) ?? []}
          selected={request.exhaustedHospitals}
          onToggle={(id) => setRequest({ ...request, exhaustedHospitals: toggleSelection(request.exhaustedHospitals, id) })}
        />

        <button className="primary-button" type="button" onClick={onRun} disabled={busy}>
          Run Simulation
        </button>
      </aside>

      <section className="simulation-main">
        <div className="comparison-grid">
          {greedy && <StrategyCard result={greedy} />}
          {global && <StrategyCard result={global} />}
          {!result && <div className="empty-state">Run a scenario to compare dispatch strategies</div>}
        </div>

        <div className="simulation-map">
          <MapPanel
            ambulances={data?.ambulances ?? []}
            liveLocations={data?.liveLocations ?? []}
            hospitals={data?.hospitals ?? []}
            incidents={simulatedIncidents}
            trips={[]}
            selectedIncidentId={simulatedIncidents[0]?.id ?? ''}
            selectedTripId=""
            compact
          />
        </div>

        <AssignmentTable result={selectedStrategy} />
      </section>

      <aside className="panel simulation-side">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">History</p>
            <h2>Runs</h2>
          </div>
          <ClipboardList size={18} />
        </div>

        <select className="history-select" value={result?.id ?? ''} onChange={(event) => setResult(data?.simulations.find((simulation) => simulation.id === event.target.value) ?? null)}>
          <option value="">Select run</option>
          {data?.simulations.map((simulation) => (
            <option key={simulation.id} value={simulation.id}>{simulation.id} - {formatDateTime(simulation.createdAt)}</option>
          ))}
        </select>

        <div className="compact-list bordered">
          <h3>Unmatched</h3>
          {unmatched.length === 0 && <div className="empty-state compact">No unmatched incidents</div>}
          {unmatched.map((assignment) => (
            <div className="incoming-row" key={assignment.incidentId}>
              <div>
                <strong>{assignment.incidentId}</strong>
                <small>{assignment.condition} - {assignment.priority}</small>
                <small>{assignment.reason}</small>
              </div>
            </div>
          ))}
        </div>

        <div className="timeline">
          <h3>Playback</h3>
          {(selectedStrategy?.assignments ?? []).slice(0, 8).map((assignment, index) => (
            <div className="timeline-row" key={`${assignment.strategy}-${assignment.incidentId}`}>
              <span />
              <div>
                <strong>{index + 1}. {assignment.incidentId}</strong>
                <small>{assignment.matched ? `${assignment.ambulanceId} to ${assignment.hospitalId}` : 'Unmatched'}</small>
                <small>{assignment.matched ? `${assignment.pickupEtaMinutes} min pickup` : assignment.reason}</small>
              </div>
            </div>
          ))}
        </div>
      </aside>
    </section>
  );
}

function SelectorGroup({
  title,
  items,
  selected,
  onToggle
}: {
  title: string;
  items: { id: string; label: string }[];
  selected: string[];
  onToggle: (id: string) => void;
}) {
  return (
    <div className="selector-group">
      <h3>{title}</h3>
      {items.map((item) => (
        <label key={item.id}>
          <input type="checkbox" checked={selected.includes(item.id)} onChange={() => onToggle(item.id)} />
          <span>{item.label}</span>
        </label>
      ))}
    </div>
  );
}

function StrategyCard({ result }: { result: SimulationStrategyResult }) {
  return (
    <article className="strategy-card">
      <span className="status-badge assigned">{formatStrategy(result.strategy)}</span>
      <div className="strategy-numbers">
        <InfoLine label="Matched" value={String(result.matchedCount)} />
        <InfoLine label="Unmatched" value={String(result.unmatchedCount)} />
        <InfoLine label="Avg Pickup" value={`${result.averagePickupEtaMinutes} min`} />
        <InfoLine label="Avg Transfer" value={`${result.averageTransferEtaMinutes} min`} />
        <InfoLine label="Total Cost" value={String(result.totalCost)} />
        <InfoLine label="Improvement" value={`${result.improvementPercent}%`} />
      </div>
    </article>
  );
}

function AssignmentTable({ result }: { result: SimulationStrategyResult | null }) {
  if (!result) {
    return <div className="empty-state">No assignments yet</div>;
  }

  return (
    <div className="assignment-table">
      <div className="assignment-header">
        <span>Incident</span>
        <span>Match</span>
        <span>ETA</span>
        <span>Cost</span>
      </div>
      {result.assignments.map((assignment) => (
        <div className={`assignment-row ${assignment.matched ? '' : 'unmatched'}`} key={`${assignment.strategy}-${assignment.incidentId}`}>
          <span>
            <strong>{assignment.incidentId}</strong>
            <small>{assignment.condition} - {assignment.priority}</small>
          </span>
          <span>{assignment.matched ? `${assignment.ambulanceId} -> ${assignment.hospitalId}` : 'Unmatched'}</span>
          <span>{assignment.matched ? `${assignment.pickupEtaMinutes} / ${assignment.hospitalEtaMinutes} min` : '-'}</span>
          <span>{assignment.matched ? assignment.totalCost : '-'}</span>
        </div>
      ))}
    </div>
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
  const [locationMessage, setLocationMessage] = useState('');

  function useCurrentLocation() {
    if (!('geolocation' in navigator)) {
      setForm({ ...form, locationSource: 'MANUAL' });
      setLocationMessage('Current location is not available in this browser.');
      return;
    }
    setLocationMessage('Requesting browser location...');
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const nextLatitude = Number(position.coords.latitude.toFixed(5));
        const nextLongitude = Number(position.coords.longitude.toFixed(5));
        setForm({
          ...form,
          latitude: nextLatitude,
          longitude: nextLongitude,
          locationSource: 'GPS'
        });
        setLocationMessage(`Using current location: ${nextLatitude.toFixed(5)}, ${nextLongitude.toFixed(5)}`);
      },
      () => {
        setForm({ ...form, locationSource: 'MANUAL' });
        setLocationMessage('Location permission was denied. You can enter address or coordinates manually.');
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 }
    );
  }

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
      <label>
        Address
        <input value={form.addressText} onChange={(event) => setForm({ ...form, addressText: event.target.value, locationSource: form.locationSource || 'MANUAL' })} />
      </label>
      <label>
        Landmark
        <input value={form.landmark} onChange={(event) => setForm({ ...form, landmark: event.target.value })} />
      </label>
      <div className="location-source-row">
        <span>{form.locationSource} - {formatCoordinates({ latitude: form.latitude, longitude: form.longitude })}</span>
        <button className="ghost-button" type="button" onClick={useCurrentLocation}>
          <Navigation size={15} />
          Use Current Location
        </button>
      </div>
      {locationMessage && <p className="form-note">{locationMessage}</p>}
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

function LiveAmbulanceRow({
  ambulance,
  liveLocation,
  onMoveAmbulance,
  busy
}: {
  ambulance: Ambulance;
  liveLocation: AmbulanceLocationSnapshot | null;
  onMoveAmbulance: (ambulance: Ambulance, deltaLatitude: number, deltaLongitude: number) => void;
  busy: boolean;
}) {
  const step = 0.006;

  return (
    <div className="resource-row live-row">
      <span className={`resource-dot ${ambulance.status.toLowerCase()}`} />
      <span>
        <strong>{ambulance.callSign}</strong>
        <small>{liveLocation ? `Live ${formatRelativeTime(liveLocation.updatedAt)}` : ambulance.baseStation}</small>
      </span>
      <div className="movement-controls" aria-label={`Move ${ambulance.callSign}`}>
        <button type="button" onClick={() => onMoveAmbulance(ambulance, step, 0)} disabled={busy} title="Move north" aria-label="Move north">
          <ArrowUp size={14} />
        </button>
        <button type="button" onClick={() => onMoveAmbulance(ambulance, 0, -step)} disabled={busy} title="Move west" aria-label="Move west">
          <ArrowLeft size={14} />
        </button>
        <button type="button" onClick={() => onMoveAmbulance(ambulance, 0, step)} disabled={busy} title="Move east" aria-label="Move east">
          <ArrowRight size={14} />
        </button>
        <button type="button" onClick={() => onMoveAmbulance(ambulance, -step, 0)} disabled={busy} title="Move south" aria-label="Move south">
          <ArrowDown size={14} />
        </button>
      </div>
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

function HospitalTripRow({ trip, data }: { trip: Trip; data: DashboardState }) {
  const incident = data.incidents.find((candidate) => candidate.id === trip.incidentId);
  const ambulance = data.ambulances.find((candidate) => candidate.id === trip.ambulanceId);

  return (
    <div className="incoming-row">
      <div>
        <strong>{incident?.patientName ?? trip.incidentId}</strong>
        <small>{ambulance?.callSign ?? trip.ambulanceId} - {formatStatus(trip.status)}</small>
      </div>
      <span className={`status-badge ${trip.status.toLowerCase()}`}>{formatStatus(trip.status)}</span>
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

function OutboxPanel({
  pending,
  published,
  summary,
  events,
  lastPublish,
  onPublish,
  busy
}: {
  pending: number;
  published: number;
  summary: OutboxSummary | null;
  events: OutboxEvent[];
  lastPublish: OutboxPublishResponse | null;
  onPublish: () => void;
  busy: boolean;
}) {
  const totalEvents = summary?.totalEvents ?? pending + published;
  const oldestPending = summary?.oldestPendingAgeSeconds ? formatDuration(summary.oldestPendingAgeSeconds) : 'None';
  const lastPublished = summary?.lastPublishedAt ? formatDateTime(summary.lastPublishedAt) : 'None';
  const readyEvents = summary?.readyEvents ?? pending;
  const failedEvents = summary?.failedEvents ?? 0;
  const retryScheduledEvents = summary?.retryScheduledEvents ?? 0;
  const latestFailure = events.find((event) => !event.publishedAt && event.lastPublishError);
  const eventTypes = summary?.eventTypes ?? [];

  return (
    <div className="outbox-panel">
      <div className="panel-heading compact-heading">
        <div>
          <p className="eyebrow">Reliability</p>
          <h3>Outbox</h3>
        </div>
        <ClipboardList size={18} />
      </div>
      <div className="outbox-stats">
        <InfoLine label="Total" value={String(totalEvents)} />
        <InfoLine label="Pending" value={String(pending)} />
        <InfoLine label="Published" value={String(published)} />
        <InfoLine label="Ready" value={String(readyEvents)} />
        <InfoLine label="Failed" value={String(failedEvents)} />
        <InfoLine label="Retry Waiting" value={String(retryScheduledEvents)} />
        <InfoLine label="Oldest Pending" value={oldestPending} />
        <InfoLine label="Last Published" value={lastPublished} />
        {latestFailure && <InfoLine label="Latest Failure" value={latestFailure.lastPublishError ?? 'None'} />}
        {latestFailure && <InfoLine label="Next Retry" value={latestFailure.nextPublishAttemptAt ? formatDateTime(latestFailure.nextPublishAttemptAt) : 'Ready now'} />}
      </div>
      {eventTypes.length > 0 && (
        <div className="event-type-list">
          {eventTypes.slice(0, 5).map((eventType) => (
            <div className="event-type-row" key={eventType.eventType}>
              <span>{eventType.eventType}</span>
              <em>{eventType.pending} pending / {eventType.failed} failed / {eventType.published} published</em>
            </div>
          ))}
        </div>
      )}
      <button className="ghost-button full-width" type="button" onClick={onPublish} disabled={busy || readyEvents === 0}>
        Publish Pending
      </button>
      {lastPublish && (
        <small className="publish-note">
          Published {lastPublish.published}; failed {lastPublish.failed}; {lastPublish.pending} pending
        </small>
      )}
    </div>
  );
}

function Timeline({ events, decisions }: { events: OutboxEvent[]; decisions: DispatchAuditRecord[] }) {
  const rows = [
    ...events.map((event) => ({
      id: event.id,
      type: event.eventType,
      subject: `${event.aggregateType} ${event.aggregateId} - ${outboxDeliveryState(event)}`,
      detail: outboxDeliveryDetail(event),
      at: event.createdAt
    })),
    ...decisions.map((decision) => ({
      id: decision.id,
      type: 'dispatch.decision',
      subject: `${decision.ambulanceId} to ${decision.hospitalId}`,
      detail: '',
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
            {row.detail && <small className="timeline-detail">{row.detail}</small>}
          </div>
        </div>
      ))}
    </div>
  );
}

function AuditPanel({ events }: { events: SecurityAuditEvent[] }) {
  return (
    <div className="audit-panel">
      <div className="panel-heading compact-heading">
        <div>
          <p className="eyebrow">Security</p>
          <h3>Audit</h3>
        </div>
        <UserRound size={18} />
      </div>
      {events.length === 0 && <div className="empty-state compact">No audit events</div>}
      {events.slice(0, 8).map((event) => (
        <div className={`audit-row ${event.outcome.toLowerCase()}`} key={event.id}>
          <span>
            <strong>{event.action}</strong>
            <small>{event.actorUserId} - {event.actorRole}</small>
          </span>
          <span>
            <em>{event.outcome}</em>
            <small>{event.resourceType} {event.resourceId}</small>
          </span>
        </div>
      ))}
    </div>
  );
}

function PlatformServicesPanel({ services }: { services: PlatformServiceDescriptor[] }) {
  if (services.length === 0) return null;

  return (
    <div className="platform-panel">
      <div className="panel-heading compact-heading">
        <div>
          <p className="eyebrow">Runtime</p>
          <h3>Services</h3>
        </div>
        <RadioTower size={18} />
      </div>
      <div className="service-list">
        {services.map((service) => (
          <article className="service-row" key={service.name}>
            <span>
              <strong>{service.name}</strong>
              <small>{service.responsibility}</small>
            </span>
            <em>{service.baseUrl.replace(/^https?:\/\//, '')}</em>
          </article>
        ))}
      </div>
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
  liveLocations,
  hospitals,
  incidents,
  trips,
  selectedIncidentId,
  selectedTripId,
  compact = false
}: {
  ambulances: Ambulance[];
  liveLocations: AmbulanceLocationSnapshot[];
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
          <AmbulanceMarker
            key={ambulance.id}
            ambulance={ambulance}
            selected={ambulance.id === selectedTrip?.ambulanceId}
            liveLocation={liveLocations.find((location) => location.ambulanceId === ambulance.id) ?? null}
          />
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

function AmbulanceMarker({
  ambulance,
  selected,
  liveLocation
}: {
  ambulance: Ambulance;
  selected: boolean;
  liveLocation: AmbulanceLocationSnapshot | null;
}) {
  return (
    <CircleMarker
      center={[ambulance.location.latitude, ambulance.location.longitude]}
      radius={selected ? 11 : ambulance.status === 'AVAILABLE' ? 8 : 6}
      pathOptions={{ color: liveLocation ? '#0f766e' : '#1d4ed8', fillColor: ambulance.status === 'AVAILABLE' ? '#3b82f6' : '#94a3b8', fillOpacity: 0.9 }}
    >
      <Popup>
        <strong>{ambulance.callSign}</strong>
        <br />
        {ambulance.type} - {formatStatus(ambulance.status)}
        <br />
        {liveLocation ? `Live ${formatRelativeTime(liveLocation.updatedAt)}` : ambulance.baseStation}
      </Popup>
    </CircleMarker>
  );
}

function roleFromPath(pathname: string, user?: AuthenticatedUser): Role {
  const role = pathname.replace('/', '') as Role;
  if (!user) {
    return roles.some((candidate) => candidate.id === role) ? role : 'patient';
  }
  const allowed = allowedRolesForUser(user);
  return allowed.includes(role) ? role : allowed[0];
}

function rolePath(role: Role) {
  return `/${role}`;
}

function notificationRoleFor(role: Role): NotificationRole {
  return (role === 'simulation' ? 'CONTROL' : role.toUpperCase()) as NotificationRole;
}

function allowedRolesForUser(user: AuthenticatedUser): Role[] {
  return switchRole(user.role);
}

function switchRole(role: AuthenticatedUser['role']): Role[] {
  switch (role) {
    case 'PATIENT':
      return ['patient'];
    case 'DRIVER':
      return ['driver'];
    case 'HOSPITAL':
      return ['hospital'];
    case 'CONTROL':
      return ['control', 'simulation'];
  }
}

function toggleSelection(values: string[], value: string) {
  return values.includes(value)
    ? values.filter((candidate) => candidate !== value)
    : [...values, value];
}

function formatStatus(value: string) {
  return value.toLowerCase().split('_').map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(' ');
}

function approvalRoleLabel(role: AuthenticatedUser['role']) {
  if (role === 'DRIVER') return 'Ambulance';
  return formatStatus(role);
}

function formatStrategy(value: OptimizationStrategy) {
  return value === 'GREEDY_SEQUENTIAL' ? 'Greedy Sequential' : 'Global Min Cost';
}

function outboxDeliveryState(event: OutboxEvent) {
  if (event.publishedAt) return 'published';
  if (event.lastPublishError) return 'failed, retry scheduled';
  if (event.nextPublishAttemptAt && new Date(event.nextPublishAttemptAt).getTime() > Date.now()) return 'retry waiting';
  return 'pending';
}

function outboxDeliveryDetail(event: OutboxEvent) {
  const parts = [`attempts ${event.publishAttempts}`];
  if (event.lastPublishError) {
    parts.push(event.lastPublishError);
  }
  if (!event.publishedAt && event.nextPublishAttemptAt) {
    parts.push(`retry ${formatDateTime(event.nextPublishAttemptAt)}`);
  }
  return parts.join(' - ');
}

function formatDuration(seconds: number) {
  const safeSeconds = Math.max(0, seconds);
  if (safeSeconds < 60) return `${safeSeconds}s`;
  if (safeSeconds < 3600) return `${Math.floor(safeSeconds / 60)}m`;
  return `${Math.floor(safeSeconds / 3600)}h ${Math.floor((safeSeconds % 3600) / 60)}m`;
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString();
}

function formatCoordinates(location: { latitude: number; longitude: number }) {
  return `${location.latitude.toFixed(5)}, ${location.longitude.toFixed(5)}`;
}

function formatDistance(kilometers: number) {
  if (!Number.isFinite(kilometers)) return 'unknown';
  if (kilometers < 1) return `${Math.round(kilometers * 1000)} m`;
  return `${kilometers.toFixed(1)} km`;
}

function distanceKm(
  from: { latitude: number; longitude: number },
  to: { latitude: number; longitude: number }
) {
  const earthRadiusKm = 6371;
  const deltaLatitude = toRadians(to.latitude - from.latitude);
  const deltaLongitude = toRadians(to.longitude - from.longitude);
  const fromLatitude = toRadians(from.latitude);
  const toLatitude = toRadians(to.latitude);
  const haversine =
    Math.sin(deltaLatitude / 2) ** 2 +
    Math.cos(fromLatitude) * Math.cos(toLatitude) * Math.sin(deltaLongitude / 2) ** 2;
  return earthRadiusKm * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
}

function toRadians(degrees: number) {
  return degrees * (Math.PI / 180);
}

function formatRelativeTime(value: string) {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  return `${Math.floor(seconds / 3600)}h ago`;
}
