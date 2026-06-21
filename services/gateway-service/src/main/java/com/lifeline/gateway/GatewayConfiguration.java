package com.lifeline.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class GatewayConfiguration {
    @Bean
    RestClient operationsRestClient(RestClient.Builder builder, GatewayProperties properties) {
        return builder.baseUrl(properties.operationsUrl().toString()).build();
    }

    @Bean
    WebMvcConfigurer corsConfigurer(GatewayProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] origins = Arrays.stream(properties.allowedOrigins().split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
                        .toArray(String[]::new);

                registry.addMapping("/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("Authorization", "Content-Type", "Accept", "Origin")
                        .exposedHeaders("Location")
                        .maxAge(3600);
            }
        };
    }
}
