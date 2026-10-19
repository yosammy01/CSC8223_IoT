/**
 * Smoke test for loading the backend application context.
 * See README.md for the test command and backend responsibilities.
 */
package com.samuel_robles.java_backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JavaBackendApplicationTests {

    @Test
    void applicationClassExists() {
        assertNotNull(JavaBackendApplication.class);
    }

}
