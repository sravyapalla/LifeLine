package com.lifeline;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("memory")
class LifeLineApplicationMemoryProfileTest {
    @Test
    void startsWithMemoryProfile() {
    }
}
