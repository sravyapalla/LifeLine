package com.lifeline.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    private final JwtService jwtService = new JwtService(
            new ObjectMapper().findAndRegisterModules(),
            "test-secret",
            60
    );

    @Test
    void issuesAndVerifiesSignedToken() {
        AuthenticatedUser user = new AuthenticatedUser("driver.demo", "Driver Demo", UserRole.DRIVER, "AMB-101", null);

        JwtService.TokenIssue token = jwtService.issue(user, Instant.parse("2026-06-21T08:00:00Z"));

        assertThat(jwtService.verify(token.token(), Instant.parse("2026-06-21T08:30:00Z")))
                .contains(user);
    }

    @Test
    void rejectsExpiredToken() {
        AuthenticatedUser user = new AuthenticatedUser("control.demo", "Control Demo", UserRole.CONTROL, null, null);

        JwtService.TokenIssue token = jwtService.issue(user, Instant.parse("2026-06-21T08:00:00Z"));

        assertThat(jwtService.verify(token.token(), Instant.parse("2026-06-21T09:01:00Z")))
                .isEmpty();
    }

    @Test
    void rejectsTamperedToken() {
        AuthenticatedUser user = new AuthenticatedUser("patient.demo", "Patient Demo", UserRole.PATIENT, null, null);

        JwtService.TokenIssue token = jwtService.issue(user, Instant.parse("2026-06-21T08:00:00Z"));

        assertThat(jwtService.verify(token.token() + "x", Instant.parse("2026-06-21T08:01:00Z")))
                .isEmpty();
    }
}
