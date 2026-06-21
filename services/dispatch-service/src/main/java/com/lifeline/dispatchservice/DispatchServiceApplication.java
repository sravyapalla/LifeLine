package com.lifeline.dispatchservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.lifeline")
@ConfigurationPropertiesScan(basePackages = "com.lifeline")
public class DispatchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DispatchServiceApplication.class, args);
    }
}
