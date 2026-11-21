/**
 * Keeps the latest received MQTT message available through the REST API.
 * See README.md for the corresponding endpoint.
 */
package com.samuel_robles.java_backend.mqtt;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
/** Defines the MqttMessageStore component. */
public class MqttMessageStore {

    /** Stores the latest received MQTT message. */
    private final AtomicReference<ReceivedMqttMessage> latestMessage = new AtomicReference<>();

    /** Performs the updateFromMessage operation. */
    public void updateFromMessage(Message<?> message) {
        Object rawPayload = message.getPayload();
        String payload = rawPayload instanceof byte[] bytes
                ? new String(bytes, StandardCharsets.UTF_8)
                : String.valueOf(rawPayload);
        String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
        Integer qos = message.getHeaders().get(MqttHeaders.RECEIVED_QOS, Integer.class);
        Boolean retained = message.getHeaders().get(MqttHeaders.RECEIVED_RETAINED, Boolean.class);

        latestMessage.set(new ReceivedMqttMessage(
                topic == null ? "" : topic,
                payload,
                qos == null ? 0 : qos,
                retained != null && retained,
                Instant.now()));
    }

    /** Performs the latestMessage operation. */
    public Optional<ReceivedMqttMessage> latestMessage() {
        return Optional.ofNullable(latestMessage.get());
    }

    /** Defines the ReceivedMqttMessage component. */
    public record ReceivedMqttMessage(String topic, String payload, int qos, boolean retained, Instant receivedAt) {
    }
}
