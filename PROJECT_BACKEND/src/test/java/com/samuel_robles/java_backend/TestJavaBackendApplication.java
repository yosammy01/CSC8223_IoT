/**
 * Optional Testcontainers-aware Spring Boot bootstrap.
 * This entry point is not used by the current Maven tests: they use JUnit
 * directly, and TestcontainersConfiguration is currently empty.
 */
package com.samuel_robles.java_backend;

import org.springframework.boot.SpringApplication;

public class TestJavaBackendApplication {

    /** Test-only alternative entry point; not used by the normal Spring Boot application or current Maven tests. */
    public static void main(String[] args) {
        SpringApplication.from(JavaBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
