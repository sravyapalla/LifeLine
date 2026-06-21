package com.lifeline.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {
    private final GatewayProperties properties;

    public PlatformController(GatewayProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/services")
    public PlatformServices services() {
        return new PlatformServices(List.of(
                service("gateway", "Browser API edge, CORS boundary, and service registry", URI.create("http://localhost:8088")),
                service("operations", "Current authenticated workflow API and source-compatible V7 operations runtime", properties.operationsUrl()),
                service("incident", "Incident intake, triage, and patient ownership boundary", properties.incidentUrl()),
                service("resource", "Ambulance fleet, live locations, and hospital capacity boundary", properties.resourceUrl()),
                service("dispatch", "Matching, scoring, routing, reservation, and reroute boundary", properties.dispatchUrl()),
                service("notification", "Role-targeted notification inbox and delivery adapters", properties.notificationUrl()),
                service("audit", "Security and workflow audit event boundary", properties.auditUrl()),
                service("simulation", "Surge scenario generation and optimization comparison boundary", properties.simulationUrl())
        ));
    }

    private ServiceDescriptor service(String name, String responsibility, URI baseUrl) {
        return new ServiceDescriptor(name, responsibility, baseUrl.toString(), baseUrl.resolve("/actuator/health").toString());
    }

    public record PlatformServices(List<ServiceDescriptor> services) {
    }

    public record ServiceDescriptor(String name, String responsibility, String baseUrl, String healthUrl) {
    }
}
