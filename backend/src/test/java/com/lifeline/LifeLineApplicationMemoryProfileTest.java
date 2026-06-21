package com.lifeline;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("memory")
class LifeLineApplicationMemoryProfileTest {
    private static final String DEMO_PASSWORD = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        registry.add("lifeline.security.demo-password", () -> DEMO_PASSWORD);
    }

    @Test
    void startsWithMemoryProfile() {
    }
}
