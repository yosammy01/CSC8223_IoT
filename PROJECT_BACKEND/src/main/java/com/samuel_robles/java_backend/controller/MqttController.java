package com.samuel_robles.java_backend.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

import com.samuel_robles.java_backend.mqtt.MqttMessageStore;
import com.samuel_robles.java_backend.mqtt.MqttPublisher;

@RestController
@RequestMapping("/api/mqtt")
public class MqttController {

    private final MqttPublisher mqttPublisher;
    private final MqttMessageStore mqttMessageStore;

    public MqttController(MqttPublisher mqttPublisher, MqttMessageStore mqttMessageStore) {
        this.mqttPublisher = mqttPublisher;
        this.mqttMessageStore = mqttMessageStore;
    }

    @PostMapping("/publish")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PublishResponse publish(@RequestBody PublishRequest request) {
        if (request == null || !StringUtils.hasText(request.payload())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload is required.");
        }

        mqttPublisher.publish(request.payload(), request.topic(), request.qos(), request.retained());
        return new PublishResponse("queued", Instant.now());
    }

    @GetMapping("/latest")
    public LatestMessageResponse latest() {
        return new LatestMessageResponse(mqttMessageStore.latestMessage().orElse(null), Instant.now());
    }

    public record PublishRequest(String payload, String topic, Integer qos, Boolean retained) {
    }

    public record PublishResponse(String status, Instant timestamp) {
    }

    public record LatestMessageResponse(MqttMessageStore.ReceivedMqttMessage message, Instant timestamp) {
    }
}
