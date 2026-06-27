package com.lifeline.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
                "driver.demo", new DemoUser("driver.demo", "Driver Demo", passwordHash, UserRole.DRIVER, "AMB-101", null),
                "hospital.demo", new DemoUser("hospital.demo", "Hospital Demo", passwordHash, UserRole.HOSPITAL, null, "HOS-201"),
                "control.demo", new DemoUser("control.demo", "Control Demo", passwordHash, UserRole.CONTROL, null, null)
        ));
    }

    public Optional<DemoUser> find(String username) {
        return Optional.ofNullable(users.get(username));
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
}
