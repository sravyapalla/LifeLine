package com.lifeline.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.sql.ResultSet;
import java.sql.SQLException;

@Service
public class DemoUserDirectory {
    private final Map<String, DemoUser> users;
    private final JdbcTemplate jdbc;
    private boolean jdbcSeeded;

    public DemoUserDirectory(
            PasswordEncoder passwordEncoder,
            @Value("${lifeline.security.demo-password}") String demoPassword,
            ObjectProvider<JdbcTemplate> jdbcProvider
    ) {
        String passwordHash = passwordEncoder.encode(demoPassword);
        this.users = new LinkedHashMap<>();
        this.jdbc = jdbcProvider.getIfAvailable();
        addSeedUser(new DemoUser("patient.demo", "Patient Demo", passwordHash, UserRole.PATIENT, null, null));
        addSeedUser(new DemoUser("driver.demo", "Ambulance Demo", passwordHash, UserRole.DRIVER, "AMB-101", null));
        addSeedUser(new DemoUser("hospital.demo", "Hospital Demo", passwordHash, UserRole.HOSPITAL, null, "HOS-201"));
        addSeedUser(new DemoUser("control.demo", "Control Demo", passwordHash, UserRole.CONTROL, null, null));
    }

    public synchronized Optional<DemoUser> find(String username, UserRole role) {
        if (jdbc != null) {
            ensureJdbcSeeded();
            return jdbc.query("""
                    SELECT username, display_name, password_hash, role, ambulance_id, hospital_id, status
                    FROM user_accounts
                    WHERE username = ? AND role = ?
                    """, resultSet -> resultSet.next() ? Optional.of(mapUser(resultSet)) : Optional.empty(), normalize(username), role.name());
        }
        return Optional.ofNullable(users.get(key(username, role)));
    }

    public synchronized List<DemoUser> pendingOperationalUsers() {
        if (jdbc != null) {
            ensureJdbcSeeded();
            return jdbc.query("""
                    SELECT username, display_name, password_hash, role, ambulance_id, hospital_id, status
                    FROM user_accounts
                    WHERE status = 'PENDING' AND role IN ('DRIVER', 'HOSPITAL')
                    ORDER BY username, role
                    """, (resultSet, rowNum) -> mapUser(resultSet));
        }
        return users.values().stream()
                .filter(user -> "PENDING".equals(user.status()))
                .filter(user -> user.role() == UserRole.DRIVER || user.role() == UserRole.HOSPITAL)
                .toList();
    }

    public synchronized Set<String> assignedAmbulanceIds() {
        if (jdbc != null) {
            ensureJdbcSeeded();
            return new HashSet<>(jdbc.queryForList("""
                    SELECT DISTINCT ambulance_id
                    FROM user_accounts
                    WHERE ambulance_id IS NOT NULL AND ambulance_id <> ''
                    """, String.class));
        }
        return users.values().stream()
                .map(DemoUser::ambulanceId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
    }

    public synchronized Set<String> assignedHospitalIds() {
        if (jdbc != null) {
            ensureJdbcSeeded();
            return new HashSet<>(jdbc.queryForList("""
                    SELECT DISTINCT hospital_id
                    FROM user_accounts
                    WHERE hospital_id IS NOT NULL AND hospital_id <> ''
                    """, String.class));
        }
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
        String normalizedUsername = normalize(username);
        if (jdbc != null) {
            ensureJdbcSeeded();
            int inserted = jdbc.update("""
                    INSERT INTO user_accounts (username, role, display_name, password_hash, ambulance_id, hospital_id, status)
                    VALUES (?, ?, ?, ?, NULL, NULL, ?)
                    ON CONFLICT (username, role) DO NOTHING
                    """, normalizedUsername, role.name(), displayName, passwordHash, status);
            if (inserted == 0) {
                throw new IllegalStateException("An account already exists for this email and role.");
            }
            return find(normalizedUsername, role)
                    .orElseThrow(() -> new IllegalStateException("Registered account could not be loaded."));
        }
        if (users.containsKey(key(normalizedUsername, role))) {
            throw new IllegalStateException("An account already exists for this email and role.");
        }
        DemoUser user = new DemoUser(normalizedUsername, displayName, passwordHash, role, null, null, status);
        users.put(key(user), user);
        return user;
    }

    public synchronized DemoUser approve(String username, UserRole role, String ambulanceId, String hospitalId) {
        DemoUser user = find(username, role)
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
        if (jdbc != null) {
            int updated = jdbc.update("""
                    UPDATE user_accounts
                    SET ambulance_id = ?, hospital_id = ?, status = 'APPROVED', updated_at = CURRENT_TIMESTAMP
                    WHERE username = ? AND role = ? AND status = 'PENDING'
                    """, ambulanceId, hospitalId, normalize(username), role.name());
            if (updated == 0) {
                throw new IllegalStateException("Signup request is already reviewed.");
            }
            return find(username, role)
                    .orElseThrow(() -> new IllegalStateException("Approved account could not be loaded."));
        }
        users.put(key(approved), approved);
        return approved;
    }

    private void addSeedUser(DemoUser user) {
        users.put(key(user), user);
    }

    private void ensureJdbcSeeded() {
        if (jdbc == null || jdbcSeeded) {
            return;
        }
        for (DemoUser user : users.values()) {
            jdbc.update("""
                    INSERT INTO user_accounts (username, role, display_name, password_hash, ambulance_id, hospital_id, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (username, role) DO UPDATE
                    SET display_name = EXCLUDED.display_name,
                        password_hash = EXCLUDED.password_hash,
                        ambulance_id = EXCLUDED.ambulance_id,
                        hospital_id = EXCLUDED.hospital_id,
                        status = EXCLUDED.status,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    normalize(user.username()),
                    user.role().name(),
                    user.displayName(),
                    user.passwordHash(),
                    user.ambulanceId(),
                    user.hospitalId(),
                    user.status()
            );
        }
        jdbcSeeded = true;
    }

    private DemoUser mapUser(ResultSet resultSet) throws SQLException {
        return new DemoUser(
                resultSet.getString("username"),
                resultSet.getString("display_name"),
                resultSet.getString("password_hash"),
                UserRole.valueOf(resultSet.getString("role")),
                resultSet.getString("ambulance_id"),
                resultSet.getString("hospital_id"),
                resultSet.getString("status")
        );
    }

    private String key(DemoUser user) {
        return key(user.username(), user.role());
    }

    private String key(String username, UserRole role) {
        return normalize(username) + "|" + role.name();
    }

    private String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
