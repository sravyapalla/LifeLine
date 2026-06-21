package com.lifeline.platform;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/internal/service")
public class ServiceMetadataController {
    private final ServiceMetadataProperties metadata;

    public ServiceMetadataController(ServiceMetadataProperties metadata) {
        this.metadata = metadata;
    }

    @GetMapping("/metadata")
    public ServiceMetadataProperties metadata() {
        return metadata;
    }

    @GetMapping("/readiness")
    public ServiceReadiness readiness() {
        return new ServiceReadiness(metadata.name(), "SCAFFOLD_READY", Instant.now(), metadata.dependencies());
    }

    public record ServiceReadiness(String service, String status, Instant checkedAt, Iterable<String> dependencies) {
    }
}
