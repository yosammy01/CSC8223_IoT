/**
 * Exposes the backend information and health-check endpoints.
 * See README.md for the API contract and curl examples.
 */
package com.samuel_robles.java_backend.controller;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
/** Defines the ApiController component. */
public class ApiController {

    /** Stores the applicationName value. */
    /** Stores the configured application name. */
    private final String applicationName;

    /** Performs the ApiController operation. */
    public ApiController(@Value("${spring.application.name:JAVA_BACKEND}") String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * Manually used to verify that the Spring Boot server is running and responding.
     * It also returns basic application metadata, status, and version information.
     */
    @GetMapping
    public ApiInfoResponse root() {
        return new ApiInfoResponse(
                applicationName,
                "running",
                "The server is running.",
                "v1",
                Instant.now());
    }

    @GetMapping("/health")
    /** Performs the health operation. */
    public ApiHealthResponse health() {
        return new ApiHealthResponse("UP", Instant.now());
    }

    /** Defines the ApiInfoResponse component. */
    public record ApiInfoResponse(String application, String status, String message, String version, Instant timestamp) {
    }

    /** Defines the ApiHealthResponse component. */
    public record ApiHealthResponse(String status, Instant timestamp) {
    }

}
