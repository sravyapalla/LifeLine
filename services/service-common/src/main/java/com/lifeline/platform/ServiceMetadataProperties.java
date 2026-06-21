package com.lifeline.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "lifeline.service")
public record ServiceMetadataProperties(
        String name,
        String domain,
        String responsibility,
        List<String> owns,
        List<String> consumes,
        List<String> publishes,
        List<String> publicApis,
        List<String> dependencies
) {
    public ServiceMetadataProperties {
        owns = safe(owns);
        consumes = safe(consumes);
        publishes = safe(publishes);
        publicApis = safe(publicApis);
        dependencies = safe(dependencies);
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
