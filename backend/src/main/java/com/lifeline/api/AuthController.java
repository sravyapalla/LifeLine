package com.lifeline.api;

import com.lifeline.security.AuthenticatedUser;
import com.lifeline.security.CurrentUserService;
import com.lifeline.security.DemoUser;
import com.lifeline.security.DemoUserDirectory;
import com.lifeline.security.JwtService;
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

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final DemoUserDirectory userDirectory;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    public AuthController(
            DemoUserDirectory userDirectory,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CurrentUserService currentUserService
    ) {
        this.userDirectory = userDirectory;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        DemoUser user = userDirectory.find(request.username())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.passwordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password."));
        AuthenticatedUser authenticatedUser = user.authenticatedUser();
        JwtService.TokenIssue token = jwtService.issue(authenticatedUser, Instant.now());
        return new AuthResponse(token.token(), token.expiresAt(), authenticatedUser);
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public AuthenticatedUser me() {
        return currentUserService.currentUser();
    }
}
