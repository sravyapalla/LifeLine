package com.lifeline.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlatformController.class)
class PlatformControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesConfiguredServiceRegistry() throws Exception {
        mockMvc.perform(get("/api/platform/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[*].name", hasItem("gateway")))
                .andExpect(jsonPath("$.services[*].name", hasItem("operations")))
                .andExpect(jsonPath("$.services[*].name", hasItem("simulation")));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        GatewayProperties gatewayProperties() {
            return new GatewayProperties(
                    "http://localhost:5173",
                    URI.create("http://operations:8080"),
                    URI.create("http://incident:8091"),
                    URI.create("http://resource:8092"),
                    URI.create("http://dispatch:8093"),
                    URI.create("http://notification:8094"),
                    URI.create("http://audit:8095"),
                    URI.create("http://simulation:8096")
            );
        }
    }
}
