package com.lifeline.api;

import com.lifeline.dispatch.DispatchDecision;
import com.lifeline.dispatch.DispatchEngine;
import com.lifeline.dispatch.NoDispatchCandidateException;
import com.lifeline.domain.Ambulance;
import com.lifeline.domain.AmbulanceStatus;
import com.lifeline.domain.DispatchAuditRecord;
import com.lifeline.domain.Hospital;
import com.lifeline.domain.Incident;
import com.lifeline.domain.IncidentStatus;
import com.lifeline.domain.Location;
import com.lifeline.domain.OutboxEvent;
import com.lifeline.domain.Trip;
import com.lifeline.store.LifeLineStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LifeLineController {
    private final LifeLineStore store;
    private final DispatchEngine dispatchEngine;

    public LifeLineController(LifeLineStore store, DispatchEngine dispatchEngine) {
        this.store = store;
        this.dispatchEngine = dispatchEngine;
    }

    @GetMapping("/ambulances")
    public List<Ambulance> ambulances() {
        return store.ambulances();
    }

    @GetMapping("/hospitals")
    public List<Hospital> hospitals() {
        return store.hospitals();
    }

    @GetMapping("/incidents")
    public List<Incident> incidents() {
        return store.incidents();
    }

    @GetMapping("/trips")
    public List<Trip> trips() {
        return store.trips();
    }

    @GetMapping("/dispatch-decisions")
    public List<DispatchAuditRecord> dispatchDecisions() {
        return store.dispatchDecisions();
    }

    @GetMapping("/outbox-events")
    public List<OutboxEvent> outboxEvents() {
        return store.outboxEvents();
    }

    @GetMapping("/metrics")
    public MetricsResponse metrics() {
        List<Incident> incidents = store.incidents();
        List<Ambulance> ambulances = store.ambulances();
        List<Hospital> hospitals = store.hospitals();
        List<Trip> trips = store.trips();

        double averageBedAvailability = hospitals.stream()
                .mapToDouble(hospital -> hospital.totalBeds() == 0 ? 0 : (double) hospital.availableBeds() / hospital.totalBeds())
                .average()
                .orElse(0);

        return new MetricsResponse(
                (int) incidents.stream().filter(incident -> incident.status() == IncidentStatus.NEW).count(),
                (int) ambulances.stream().filter(ambulance -> ambulance.status() == AmbulanceStatus.AVAILABLE).count(),
                trips.size(),
                (int) hospitals.stream().filter(Hospital::hasCapacity).count(),
                Math.round(averageBedAvailability * 1000.0) / 10.0
        );
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public Incident createIncident(@Valid @RequestBody CreateIncidentRequest request) {
        return store.createIncident(
                request.patientName(),
                request.phone(),
                request.condition(),
                request.priority(),
                new Location(request.latitude(), request.longitude())
        );
    }

    @PostMapping("/dispatch")
    public DispatchResponse dispatch(@Valid @RequestBody DispatchRequest request) {
        Incident incident = store.findIncident(request.incidentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));

        DispatchDecision decision = dispatchEngine.decide(incident, store.ambulances(), store.hospitals());
        Trip trip = store.commitAssignment(
                incident.id(),
                decision.ambulance().id(),
                decision.hospital().id(),
                decision.winningScore(),
                decision.alternatives()
        );

        Incident updatedIncident = store.findIncident(incident.id()).orElseThrow();
        Ambulance updatedAmbulance = store.findAmbulance(decision.ambulance().id()).orElseThrow();
        Hospital updatedHospital = store.findHospital(decision.hospital().id()).orElseThrow();

        return new DispatchResponse(
                updatedIncident,
                updatedAmbulance,
                updatedHospital,
                trip,
                decision.winningScore(),
                decision.alternatives()
        );
    }

    @PostMapping("/demo/reset")
    public void resetDemo() {
        store.reset();
    }

    @ExceptionHandler(NoDispatchCandidateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleNoCandidate(NoDispatchCandidateException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIllegalState(IllegalStateException exception) {
        return new ErrorResponse(exception.getMessage());
    }
}
