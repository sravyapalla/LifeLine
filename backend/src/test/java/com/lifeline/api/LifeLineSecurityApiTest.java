package com.lifeline.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeline.location.AmbulanceLocationProjection;
import com.lifeline.store.LifeLineStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("memory")
class LifeLineSecurityApiTest {
    private static final String DEMO_PASSWORD = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LifeLineStore store;

    @Autowired
    private AmbulanceLocationProjection ambulanceLocationProjection;

    @BeforeEach
    void resetDemoState() {
        store.reset();
        ambulanceLocationProjection.clear();
    }

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        registry.add("lifeline.security.demo-password", () -> DEMO_PASSWORD);
    }

    @Test
    void loginReturnsTokenAndCurrentUser() throws Exception {
        String token = login("patient.demo");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("patient.demo")))
                .andExpect(jsonPath("$.role", is("PATIENT")));
    }

    @Test
    void invalidLoginReturnsConsistentJsonAndAuditCapturesFailure() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "patient.demo", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid username or password.")));

        String controlToken = login("control.demo");
        mockMvc.perform(get("/api/audit-events")
                        .header("Authorization", bearer(controlToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("auth.login")))
                .andExpect(jsonPath("$[*].outcome", hasItem("DENIED")));
    }

    @Test
    void patientCannotReadAnotherRoleNotificationsAndDeniedAttemptIsAudited() throws Exception {
        String patientToken = login("patient.demo");
        mockMvc.perform(get("/api/notifications?role=control")
                        .header("Authorization", bearer(patientToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        String controlToken = login("control.demo");
        mockMvc.perform(get("/api/audit-events")
                        .header("Authorization", bearer(controlToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("notification.read")))
                .andExpect(jsonPath("$[*].outcome", hasItem("DENIED")));
    }

    @Test
    void driverSeesOnlyAssignedOperationalIncidentWithMaskedPii() throws Exception {
        String controlToken = login("control.demo");
        mockMvc.perform(post("/api/dispatch")
                        .header("Authorization", bearer(controlToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("incidentId", "INC-301"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip.ambulanceId", is("AMB-101")));

        String driverToken = login("driver.demo");
        mockMvc.perform(get("/api/incidents")
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is("INC-301")))
                .andExpect(jsonPath("$[0].patientName", is("Incident INC-301")))
                .andExpect(jsonPath("$[0].phone", is("****0001")));
    }

    @Test
    void patientCanReadCoverageSnapshotButOnlyOwnIncidents() throws Exception {
        String patientToken = login("patient.demo");

        mockMvc.perform(get("/api/ambulances")
                        .header("Authorization", bearer(patientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].status", is("AVAILABLE")));

        mockMvc.perform(get("/api/hospitals")
                        .header("Authorization", bearer(patientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].availableBeds", notNullValue()));

        mockMvc.perform(get("/api/trips")
                        .header("Authorization", bearer(patientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void hospitalCanUpdateOwnCapacityButNotAnotherHospital() throws Exception {
        String hospitalToken = login("hospital.demo");

        mockMvc.perform(post("/api/hospitals/HOS-201/capacity")
                        .header("Authorization", bearer(hospitalToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("availableBeds", 6))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("HOS-201")))
                .andExpect(jsonPath("$.availableBeds", is(6)));

        mockMvc.perform(post("/api/hospitals/HOS-202/capacity")
                        .header("Authorization", bearer(hospitalToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("availableBeds", 3))))
                .andExpect(status().isForbidden());
    }

    @Test
    void configuredCorsAllowsLocalFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/incidents")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")));
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", DEMO_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode payload = objectMapper.readTree(response);
        return payload.get("token").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
