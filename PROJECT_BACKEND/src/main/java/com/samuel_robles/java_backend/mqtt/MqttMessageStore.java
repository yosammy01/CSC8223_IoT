package com.samuel_robles.java_backend.mqtt;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MqttMessageStore {

    private final AtomicReference<ReceivedMqttMessage> latestMessage = new AtomicReference<>();

    public void updateFromMessage(Message<?> message) {
        String payload = String.valueOf(message.getPayload());
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

    public Optional<ReceivedMqttMessage> latestMessage() {
        return Optional.ofNullable(latestMessage.get());
    }

    public record ReceivedMqttMessage(String topic, String payload, int qos, boolean retained, Instant receivedAt) {
    }
}
