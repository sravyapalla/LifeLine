package com.lifeline.api;

import org.springframework.http.HttpStatusCode;

import java.time.Instant;

public record ErrorResponse(
        String message,
        int status,
        String error,
        Instant timestamp
) {
    public static ErrorResponse of(HttpStatusCode status, String message) {
        return new ErrorResponse(message, status.value(), defaultError(status), Instant.now());
    }

    private static String defaultError(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            default -> "Error";
        };
    }
}
