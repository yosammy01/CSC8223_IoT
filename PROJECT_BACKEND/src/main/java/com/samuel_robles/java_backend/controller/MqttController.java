/**
 * Exposes REST controls for MQTT publishing and federated-model orchestration.
 * See README.md for endpoint payloads and operational responsibilities.
 */
package com.samuel_robles.java_backend.controller;

import java.nio.charset.StandardCharsets;
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
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import com.samuel_robles.java_backend.federated.FederatedAggregationService;
import com.samuel_robles.java_backend.mqtt.MqttMessageStore;
import com.samuel_robles.java_backend.mqtt.MqttPublisher;

@RestController
@RequestMapping("/api/mqtt")
/** Defines the MqttController component. */
public class MqttController {

    /** Performs the getLogger operation. */
    private static final Logger logger = LoggerFactory.getLogger(MqttController.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("experiment.audit");

    /** Stores the mqttPublisher value. */
    private final MqttPublisher mqttPublisher;
    /** Stores the mqttMessageStore value. */
    private final MqttMessageStore mqttMessageStore;
    /** Stores the federatedAggregationService value. */
    private final FederatedAggregationService federatedAggregationService;
    /** Stores the commandsTopic value. */
    private final String commandsTopic;

    /** Performs the MqttController operation. */
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
    /** Performs the publish operation. */
    public PublishResponse publish(@RequestBody PublishRequest request) {
        if (request == null || !StringUtils.hasText(request.payload())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload is required.");
        }

        mqttPublisher.publish(request.payload(), request.topic(), request.qos(), request.retained());
        auditLogger.info("REST_AUDIT mqtt_publish topic={} qos={} retained={} payload_bytes={}",
                request.topic(), request.qos(), request.retained(), request.payload().getBytes(StandardCharsets.UTF_8).length);
        return new PublishResponse("queued", Instant.now());
    }

    @GetMapping("/latest")
    /** Performs the latest operation. */
    public LatestMessageResponse latest() {
        return new LatestMessageResponse(mqttMessageStore.latestMessage().orElse(null), Instant.now());
    }

    @PostMapping("/federated/command")
    @ResponseStatus(HttpStatus.ACCEPTED)
    /** Performs the publishFederatedCommand operation. */
    public PublishResponse publishFederatedCommand(@RequestBody JsonNode command) {
        if (command == null || !command.isObject()
                || !command.hasNonNull("command")
                || !StringUtils.hasText(command.get("command").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A JSON command is required.");
        }
        mqttPublisher.publish(command.toString(), commandsTopic, 1, false);
        auditLogger.info("REST_AUDIT federated_command_queued topic={} command={}", commandsTopic, command.get("command").asText());
        return new PublishResponse("queued", Instant.now());
    }

    @GetMapping("/federated/status")
    /** Performs the federatedStatus operation. */
    public FederatedAggregationService.FederatedStatus federatedStatus() {
        return federatedAggregationService.status();
    }

    @PostMapping("/federated/status/reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    /** Performs the resetFederatedStatus operation. */
    public PublishResponse resetFederatedStatus() {
        federatedAggregationService.resetAggregationStatus();
        auditLogger.info("REST_AUDIT federated_status_reset");
        return new PublishResponse("reset", Instant.now());
    }

    @PostMapping("/federated/model/republish")
    @ResponseStatus(HttpStatus.ACCEPTED)
    /** Performs the republishGlobalModel operation. */
    public PublishResponse republishGlobalModel() {
        federatedAggregationService.republishLatestGlobalWeights();
        auditLogger.info("REST_AUDIT global_model_republish_queued");
        return new PublishResponse("queued", Instant.now());
    }

    @PostMapping("/federated/model/republish-all")
    @ResponseStatus(HttpStatus.ACCEPTED)
    /** Performs the republishAllModels operation. */
    public PublishResponse republishAllModels() {
        federatedAggregationService.republishAllModels();
        auditLogger.info("REST_AUDIT all_models_republish_queued");
        return new PublishResponse("queued", Instant.now());
    }

    @PostMapping("/federated/model/toggle")
    @ResponseStatus(HttpStatus.ACCEPTED)
    /** Performs the toggleActiveModel operation. */
    public PublishResponse toggleActiveModel(@RequestBody ModelSelectionRequest request) {
        if (request == null || request.activeModel() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "activeModel must be LOCAL or GLOBAL.");
        }
        mqttPublisher.publish(
                "{\"command\":\"SET_MODEL\",\"active_model\":\"" + request.activeModel().name() + "\"}",
                commandsTopic,
                1,
                false);
        auditLogger.info("REST_AUDIT model_selection_queued active_model={} topic={}", request.activeModel(), commandsTopic);
        return new PublishResponse("queued", Instant.now());
    }

    @PostMapping("/federated/training")
    @ResponseStatus(HttpStatus.ACCEPTED)
    /** Performs the startTraining operation. */
    public PublishResponse startTraining(@RequestBody TrainingRequest request) {
        if (request == null || request.useQuantization() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "useQuantization must be true or false.");
        }
        mqttPublisher.publish(
                "{\"command\":\"START_TRAINING\",\"use_quantization\":"
                        + request.useQuantization() + "}",
                commandsTopic,
                1,
                false);
        auditLogger.info("REST_AUDIT training_queued quantized={} topic={}", request.useQuantization(), commandsTopic);
        return new PublishResponse("queued", Instant.now());
    }

    /** Defines the PublishRequest component. */
    public record PublishRequest(String payload, String topic, Integer qos, Boolean retained) {
    }

    /** Defines the ModelSelectionRequest component. */
    public record ModelSelectionRequest(ActiveModel activeModel) {
    }

    /** Defines the TrainingRequest component. */
    public record TrainingRequest(Boolean useQuantization) {
    }

    /** Defines the ActiveModel component. */
    public enum ActiveModel {
        /** Represents the local option. */
        LOCAL,
        /** Represents the global option. */
        GLOBAL
    }

    /** Defines the PublishResponse component. */
    public record PublishResponse(String status, Instant timestamp) {
    }

    /** Defines the LatestMessageResponse component. */
    public record LatestMessageResponse(MqttMessageStore.ReceivedMqttMessage message, Instant timestamp) {
    }
}
