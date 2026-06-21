package com.lifeline.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "lifeline.gateway")
public record GatewayProperties(
        String allowedOrigins,
        URI operationsUrl,
        URI incidentUrl,
        URI resourceUrl,
        URI dispatchUrl,
        URI notificationUrl,
        URI auditUrl,
        URI simulationUrl
) {
}
