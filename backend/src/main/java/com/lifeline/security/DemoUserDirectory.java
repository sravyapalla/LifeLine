package com.lifeline.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DemoUserDirectory {
    private final Map<String, DemoUser> users;

    public DemoUserDirectory(
            PasswordEncoder passwordEncoder,
            @Value("${lifeline.security.demo-password}") String demoPassword
    ) {
        String passwordHash = passwordEncoder.encode(demoPassword);
        this.users = new LinkedHashMap<>(Map.of(
                "patient.demo", new DemoUser("patient.demo", "Patient Demo", passwordHash, UserRole.PATIENT, null, null),
                "driver.demo", new DemoUser("driver.demo", "Ambulance Demo", passwordHash, UserRole.DRIVER, "AMB-101", null),
                "hospital.demo", new DemoUser("hospital.demo", "Hospital Demo", passwordHash, UserRole.HOSPITAL, null, "HOS-201"),
                "control.demo", new DemoUser("control.demo", "Control Demo", passwordHash, UserRole.CONTROL, null, null)
        ));
    }

    public Optional<DemoUser> find(String username) {
        return Optional.ofNullable(users.get(username));
    }

    public synchronized List<DemoUser> pendingOperationalUsers() {
        return users.values().stream()
                .filter(user -> "PENDING".equals(user.status()))
                .filter(user -> user.role() == UserRole.DRIVER || user.role() == UserRole.HOSPITAL)
                .toList();
    }

    public synchronized Set<String> assignedAmbulanceIds() {
        return users.values().stream()
                .map(DemoUser::ambulanceId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
    }

    public synchronized Set<String> assignedHospitalIds() {
        return users.values().stream()
                .map(DemoUser::hospitalId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
    }

    public synchronized DemoUser register(
            String username,
            String displayName,
            String passwordHash,
            UserRole role,
            String status
    ) {
        if (users.containsKey(username)) {
            throw new IllegalStateException("An account already exists for this email.");
        }
        DemoUser user = new DemoUser(username, displayName, passwordHash, role, null, null, status);
        users.put(username, user);
        return user;
    }

    public synchronized DemoUser approve(String username, String ambulanceId, String hospitalId) {
        DemoUser user = Optional.ofNullable(users.get(username))
                .orElseThrow(() -> new IllegalStateException("Signup request not found."));
        if (!"PENDING".equals(user.status())) {
            throw new IllegalStateException("Signup request is already reviewed.");
        }
        DemoUser approved = new DemoUser(
                user.username(),
                user.displayName(),
                user.passwordHash(),
                user.role(),
                ambulanceId,
                hospitalId,
                "APPROVED"
        );
        users.put(username, approved);
        return approved;
    }
}
