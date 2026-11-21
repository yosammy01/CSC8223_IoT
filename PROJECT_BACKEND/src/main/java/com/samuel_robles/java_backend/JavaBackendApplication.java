/**
 * Application entry point for the Spring Boot federated-learning backend.
 * See README.md for setup, configuration, and run instructions.
 */
package com.samuel_robles.java_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
/** Defines the JavaBackendApplication component. */
public class JavaBackendApplication {

    /** Performs the main operation. */
    /** Starts the Spring Boot application. */
    public static void main(String[] args) {
        SpringApplication.run(JavaBackendApplication.class, args);
    }

}
