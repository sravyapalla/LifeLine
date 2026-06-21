package com.lifeline.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long tokenTtlSeconds;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${lifeline.security.jwt-secret}") String secret,
            @Value("${lifeline.security.token-ttl-minutes:480}") long tokenTtlMinutes
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tokenTtlSeconds = tokenTtlMinutes * 60;
    }

    public TokenIssue issue(AuthenticatedUser user, Instant issuedAt) {
        Instant expiresAt = issuedAt.plusSeconds(tokenTtlSeconds);
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.username());
        payload.put("displayName", user.displayName());
        payload.put("role", user.role().name());
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        if (user.ambulanceId() != null) {
            payload.put("ambulanceId", user.ambulanceId());
        }
        if (user.hospitalId() != null) {
            payload.put("hospitalId", user.hospitalId());
        }

        String unsigned = encodeJson(header) + "." + encodeJson(payload);
        return new TokenIssue(unsigned + "." + sign(unsigned), expiresAt);
    }

    public Optional<AuthenticatedUser> verify(String token, Instant now) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String unsigned = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsigned), parts[2])) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    DECODER.decode(parts[1]),
                    new TypeReference<>() {
                    }
            );
            long expiresAt = number(payload.get("exp"));
            if (expiresAt <= now.getEpochSecond()) {
                return Optional.empty();
            }
            String username = String.valueOf(payload.get("sub"));
            String displayName = String.valueOf(payload.get("displayName"));
            UserRole role = UserRole.valueOf(String.valueOf(payload.get("role")));
            String ambulanceId = optionalString(payload.get("ambulanceId"));
            String hospitalId = optionalString(payload.get("hospitalId"));
            return Optional.of(new AuthenticatedUser(username, displayName, role, ambulanceId, hospitalId));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode JWT payload.", exception);
        }
    }

    private String sign(String unsigned) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign JWT.", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String optionalString(Object value) {
        if (value == null) {
            return null;
        }
        String string = String.valueOf(value);
        return string.isBlank() ? null : string;
    }

    public record TokenIssue(String token, Instant expiresAt) {
    }
}
