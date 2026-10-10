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
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;

import com.samuel_robles.java_backend.federated.FederatedAggregationService;
import com.samuel_robles.java_backend.mqtt.MqttMessageStore;
import com.samuel_robles.java_backend.mqtt.MqttPublisher;

@RestController
@RequestMapping("/api/mqtt")
public class MqttController {

    private final MqttPublisher mqttPublisher;
    private final MqttMessageStore mqttMessageStore;
    private final FederatedAggregationService federatedAggregationService;
    private final String commandsTopic;

    public MqttController(
            MqttPublisher mqttPublisher,
            MqttMessageStore mqttMessageStore,
            FederatedAggregationService federatedAggregationService,
            @Value("${app.mqtt.commands-topic:federated/commands}") String commandsTopic) {
        this.mqttPublisher = mqttPublisher;
        this.mqttMessageStore = mqttMessageStore;
        this.federatedAggregationService = federatedAggregationService;
        this.commandsTopic = commandsTopic;
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

    @PostMapping("/federated/command")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PublishResponse publishFederatedCommand(@RequestBody JsonNode command) {
        if (command == null || !command.isObject()
                || !command.hasNonNull("command")
                || !StringUtils.hasText(command.get("command").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A JSON command is required.");
        }
        mqttPublisher.publish(command.toString(), commandsTopic, 1, false);
        return new PublishResponse("queued", Instant.now());
    }

    @GetMapping("/federated/status")
    public FederatedAggregationService.FederatedStatus federatedStatus() {
        return federatedAggregationService.status();
    }

    @PostMapping("/federated/model/toggle")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PublishResponse toggleActiveModel(@RequestBody ModelSelectionRequest request) {
        if (request == null || request.activeModel() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "activeModel must be LOCAL or GLOBAL.");
        }
        mqttPublisher.publish(
                "{\"command\":\"SET_MODEL\",\"active_model\":\"" + request.activeModel().name() + "\"}",
                commandsTopic,
                1,
                false);
        return new PublishResponse("queued", Instant.now());
    }

    public record PublishRequest(String payload, String topic, Integer qos, Boolean retained) {
    }

    public record ModelSelectionRequest(ActiveModel activeModel) {
    }

    public enum ActiveModel {
        LOCAL,
        GLOBAL
    }

    public record PublishResponse(String status, Instant timestamp) {
    }

    public record LatestMessageResponse(MqttMessageStore.ReceivedMqttMessage message, Instant timestamp) {
    }
}
