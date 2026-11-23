/**
 * Publishes text and binary payloads through Spring Integration MQTT.
 * See README.md for default topics and publishing examples.
 */
package com.samuel_robles.java_backend.mqtt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
/** Defines the MqttPublisher component. */
public class MqttPublisher {

    private static final Logger auditLogger = LoggerFactory.getLogger("experiment.audit");

    /** Stores the mqttOutboundChannel value. */
    private final MessageChannel mqttOutboundChannel;
    /** Stores the defaultTopic value. */
    private final String defaultTopic;
    /** Stores the defaultQos value. */
    private final int defaultQos;

    /** Performs the MqttPublisher operation. */
    public MqttPublisher(
            @Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel,
            @Value("${app.mqtt.default-topic}") String defaultTopic,
            @Value("${app.mqtt.qos}") int defaultQos) {
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.defaultTopic = defaultTopic;
        this.defaultQos = defaultQos;
    }

    /** Performs the publish operation. */
    public void publish(String payload, String topic, Integer qos, Boolean retained) {
        publishPayload(payload, topic, qos, retained);
    }

    /** Performs the publish operation. */
    public void publish(byte[] payload, String topic, Integer qos, Boolean retained) {
        publishPayload(payload, topic, qos, retained);
    }

    /** Performs the publishPayload operation. */
    private void publishPayload(Object payload, String topic, Integer qos, Boolean retained) {
        String targetTopic = StringUtils.hasText(topic) ? topic : defaultTopic;
        int targetQos = qos != null ? qos : defaultQos;
        boolean targetRetained = retained != null && retained;

        Message<Object> message = MessageBuilder.withPayload(payload)
                .setHeader(MqttHeaders.TOPIC, targetTopic)
                .setHeader(MqttHeaders.QOS, targetQos)
                .setHeader(MqttHeaders.RETAINED, targetRetained)
                .build();

        boolean sent = mqttOutboundChannel.send(message);
        if (sent) {
            int payloadBytes = payload instanceof byte[] bytes
                    ? bytes.length
                    : String.valueOf(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            auditLogger.info("MQTT_AUDIT mqtt_outbound topic={} qos={} retained={} payload_bytes={}",
                    targetTopic, targetQos, targetRetained, payloadBytes);
        } else {
            throw new IllegalStateException("Failed to send MQTT message to outbound channel.");
        }
    }
}
