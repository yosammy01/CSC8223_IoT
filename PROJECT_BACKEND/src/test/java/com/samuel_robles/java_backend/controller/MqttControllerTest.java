package com.samuel_robles.java_backend.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.samuel_robles.java_backend.config.SecurityConfig;
import com.samuel_robles.java_backend.mqtt.MqttMessageStore;
import com.samuel_robles.java_backend.mqtt.MqttPublisher;

@WebMvcTest(MqttController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class MqttControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MqttPublisher mqttPublisher;

    @MockBean
    private MqttMessageStore mqttMessageStore;

    @Test
    void publishEndpointAcceptsMessage() throws Exception {
        mockMvc.perform(post("/api/mqtt/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"payload":"hello mqtt","topic":"java_backend/test","qos":1,"retained":false}
                        """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("queued"));

        verify(mqttPublisher).publish(eq("hello mqtt"), eq("java_backend/test"), eq(1), eq(false));
    }

    @Test
    void publishEndpointRejectsBlankPayload() throws Exception {
        mockMvc.perform(post("/api/mqtt/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"payload":" "}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void latestEndpointReturnsLastMessageWhenPresent() throws Exception {
        MqttMessageStore.ReceivedMqttMessage message = new MqttMessageStore.ReceivedMqttMessage(
                "java_backend/test",
                "hello",
                1,
                false,
                Instant.parse("2026-01-01T00:00:00Z"));
        when(mqttMessageStore.latestMessage()).thenReturn(Optional.of(message));

        mockMvc.perform(get("/api/mqtt/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.topic").value("java_backend/test"))
                .andExpect(jsonPath("$.message.payload").value("hello"));
    }
}
