/**
 * Tests the backend information and health REST endpoints.
 * See README.md for endpoint behavior and curl examples.
 */
package com.samuel_robles.java_backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.samuel_robles.java_backend.config.SecurityConfig;

@WebMvcTest(ApiController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootEndpointReturnsApiMetadata() throws Exception {
        mockMvc.perform(get("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("JAVA_BACKEND"))
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.message").value("Generic API scaffold ready for new functionality."))
                .andExpect(jsonPath("$.version").value("v1"));
    }

    @Test
    void healthEndpointReturnsUpStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

}
