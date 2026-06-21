package com.lifeline.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LifeLineGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(LifeLineGatewayApplication.class, args);
    }
}
