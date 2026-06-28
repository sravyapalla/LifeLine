package com.lifeline.api;

import com.lifeline.security.AuthenticatedUser;
import com.lifeline.security.CurrentUserService;
import com.lifeline.security.DemoUser;
import com.lifeline.security.DemoUserDirectory;
import com.lifeline.security.JwtService;
import com.lifeline.security.SecurityAuditService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final DemoUserDirectory userDirectory;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;
    private final SecurityAuditService auditService;

    public AuthController(
            DemoUserDirectory userDirectory,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CurrentUserService currentUserService,
            SecurityAuditService auditService
    ) {
        this.userDirectory = userDirectory;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        DemoUser user = userDirectory.find(request.username().toLowerCase(Locale.ROOT), request.role()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            auditService.anonymous(
                    request.username(),
                    "auth.login",
                    "User",
                    request.username(),
                    "DENIED",
                    "Invalid username or password.",
                    Map.of("username", request.username(), "role", request.role().name())
            );
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }
        AuthenticatedUser authenticatedUser = user.authenticatedUser();
        JwtService.TokenIssue token = jwtService.issue(authenticatedUser, Instant.now());
        auditService.allowed(
                authenticatedUser,
                "auth.login",
                "User",
                authenticatedUser.username(),
                "Login succeeded.",
                Map.of("role", authenticatedUser.role().name(), "status", authenticatedUser.status())
        );
        return new AuthResponse(token.token(), token.expiresAt(), authenticatedUser);
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        if (request.role().isControl()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Control users must be invited by an administrator.");
        }
        String status = request.role().name().equals("PATIENT") ? "APPROVED" : "PENDING";
        DemoUser user = userDirectory.register(
                request.email().toLowerCase(Locale.ROOT),
                request.displayName(),
                passwordEncoder.encode(request.password()),
                request.role(),
                status
        );
        AuthenticatedUser authenticatedUser = user.authenticatedUser();
        JwtService.TokenIssue token = jwtService.issue(authenticatedUser, Instant.now());
        auditService.allowed(
                authenticatedUser,
                "auth.signup",
                "User",
                authenticatedUser.username(),
                "Signup completed.",
                Map.of("role", authenticatedUser.role().name(), "status", authenticatedUser.status())
        );
        return new AuthResponse(token.token(), token.expiresAt(), authenticatedUser);
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public AuthenticatedUser me() {
        return currentUserService.currentUser();
    }
}
