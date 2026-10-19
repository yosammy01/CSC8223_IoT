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
public class ApiController {

    private final String applicationName;

    public ApiController(@Value("${spring.application.name:JAVA_BACKEND}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping
    public ApiInfoResponse root() {
        return new ApiInfoResponse(
                applicationName,
                "running",
                "Generic API scaffold ready for new functionality.",
                "v1",
                Instant.now());
    }

    @GetMapping("/health")
    public ApiHealthResponse health() {
        return new ApiHealthResponse("UP", Instant.now());
    }

    public record ApiInfoResponse(String application, String status, String message, String version, Instant timestamp) {
    }

    public record ApiHealthResponse(String status, Instant timestamp) {
    }

}
